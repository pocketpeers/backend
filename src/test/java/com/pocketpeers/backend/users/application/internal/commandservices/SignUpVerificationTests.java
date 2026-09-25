package com.pocketpeers.backend.users.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import com.pocketpeers.backend.users.application.internal.identityverification.IdentityVerificationService;
import com.pocketpeers.backend.users.domain.exceptions.IdentityMismatchException;
import com.pocketpeers.backend.users.domain.exceptions.UsernameAlreadyTakenException;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerification;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerificationStatus;
import org.mockito.ArgumentCaptor;
import com.pocketpeers.backend.users.domain.model.commands.RequestSignUpCommand;
import com.pocketpeers.backend.users.domain.model.entities.PendingRegistration;
import com.pocketpeers.backend.users.domain.services.SmtpService;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.PasswordResetCodeRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.PendingRegistrationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import com.pocketpeers.backend.users.application.internal.outboundservices.hashing.HashingService;
import com.pocketpeers.backend.users.application.internal.outboundservices.tokens.TokenService;
import com.pocketpeers.backend.users.domain.services.UserInformationCommandService;
import com.pocketpeers.backend.users.domain.services.UserInformationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * El alta en dos pasos, por el lado que se rompio en produccion.
 *
 * <p>La reserva del nombre de usuario mientras vive un alta pendiente estaba
 * mal planteada: no distinguia si ese pendiente era de otra persona o del
 * propio solicitante. Volver atras en la aplicacion y pedir el codigo otra vez
 * —lo mas natural del mundo si el correo tarda— chocaba contra la solicitud
 * anterior de uno mismo, y la aplicacion respondia que el usuario ya estaba
 * tomado mientras la base de datos no tenia ninguna cuenta que lo explicara.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignUpVerificationTests {

    @Mock private UserRepository userRepository;
    @Mock private HashingService hashingService;
    @Mock private TokenService tokenService;
    @Mock private RoleRepository roleRepository;
    @Mock private UserInformationCommandService userInformationCommandService;
    @Mock private UserInformationQueryService userInformationQueryService;
    @Mock private UserInformationRepository userInformationRepository;
    @Mock private PasswordResetCodeRepository passwordResetCodeRepository;
    @Mock private PendingRegistrationRepository pendingRegistrationRepository;
    @Mock private SmtpService smtpService;
    @Mock private IdentityVerificationService identityVerificationService;

    private UserCommandServiceImpl service;

    private static final String EMAIL = "salvador@correo.com";
    private static final String USERNAME = "salvador";

    @BeforeEach
    void setUp() {
        service = new UserCommandServiceImpl(userRepository, hashingService, tokenService, roleRepository,
                userInformationCommandService, userInformationQueryService, userInformationRepository,
                passwordResetCodeRepository, pendingRegistrationRepository, smtpService,
                identityVerificationService);
        when(identityVerificationService.verify(any(), anyString(), anyString(), any()))
                .thenReturn(IdentityVerification.of(IdentityVerificationStatus.PARTIAL_MATCH, LocalDateTime.now()));
        // El interruptor llega por @Value, que no corre fuera del contexto de
        // Spring: sin esto quedaria en false y el servicio crearia la cuenta de
        // una vez, que es justo el camino que estas pruebas no miran.
        ReflectionTestUtils.setField(service, "emailVerificationEnabled", true);

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userInformationRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(hashingService.encode(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
    }

    private RequestSignUpCommand command() {
        return new RequestSignUpCommand(USERNAME, "ClaveFuerte123", "Salvador", "Salinas",
                "987654321", "", EMAIL, "DNI", "71234567");
    }

    /**
     * Reintentar el propio registro tiene que funcionar.
     *
     * <p>Es el caso exacto que fallaba: el usuario pide el codigo, vuelve atras
     * y lo pide de nuevo con los mismos datos.</p>
     */
    @Test
    void retryingTheSameRegistrationIssuesANewCodeInsteadOfComplaining() {
        when(pendingRegistrationRepository.existsUsablePendingForOtherEmail(eq(USERNAME), eq(EMAIL), any()))
                .thenReturn(false);

        var verificationRequired = service.handle(command());

        assertThat(verificationRequired).isTrue();
        // La solicitud anterior se invalida y se guarda una nueva, con su codigo.
        verify(pendingRegistrationRepository).invalidatePendingFor(eq(EMAIL), any(LocalDateTime.class));
        verify(pendingRegistrationRepository).save(any(PendingRegistration.class));
        verify(smtpService).sendSignUpVerificationEmailAsync(eq(EMAIL), anyString(), anyString());
    }

    /**
     * Pero el nombre sigue reservado frente a terceros.
     *
     * <p>Sin esta mitad, arreglar el reintento habria abierto la puerta a que
     * dos personas pidieran el codigo con el mismo nombre y la segunda en
     * confirmar se estrellara despues de haberlo hecho todo bien.</p>
     */
    @Test
    void anotherPersonCannotTakeAUsernameThatIsAlreadyPending() {
        when(pendingRegistrationRepository.existsUsablePendingForOtherEmail(eq(USERNAME), eq(EMAIL), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.handle(command()))
                .isInstanceOf(UsernameAlreadyTakenException.class);

        verify(pendingRegistrationRepository, never()).save(any(PendingRegistration.class));
        verify(smtpService, never()).sendSignUpVerificationEmailAsync(anyString(), anyString(), anyString());
    }

    /** Nada se guarda ni se envia si el documento no es valido. */
    @Test
    void anInvalidDocumentStopsTheRequestBeforeSendingAnyCode() {
        var invalid = new RequestSignUpCommand(USERNAME, "ClaveFuerte123", "Salvador", "Salinas",
                "987654321", "", EMAIL, "DNI", "123");

        assertThatThrownBy(() -> service.handle(invalid))
                .isInstanceOf(IllegalArgumentException.class);

        verify(pendingRegistrationRepository, never()).save(any(PendingRegistration.class));
        verify(smtpService, never()).sendSignUpVerificationEmailAsync(anyString(), anyString(), anyString());
    }

    /**
     * Un nombre que no coincide con el DNI se rechaza sin mandar el correo.
     *
     * <p>Es lo que permite que la aplicacion muestre el dialogo de correccion
     * en vez de pasar a la pantalla del codigo.</p>
     */
    @Test
    void aNameThatDoesNotMatchTheDniStopsTheRequestBeforeSendingAnyCode() {
        when(identityVerificationService.verify(any(), anyString(), anyString(), any()))
                .thenThrow(new IdentityMismatchException());

        assertThatThrownBy(() -> service.handle(command()))
                .isInstanceOf(IdentityMismatchException.class);

        verify(pendingRegistrationRepository, never()).save(any(PendingRegistration.class));
        verify(smtpService, never()).sendSignUpVerificationEmailAsync(anyString(), anyString(), anyString());
    }

    /** El resultado de la verificacion viaja con el alta pendiente hasta la confirmacion. */
    @Test
    void theVerificationResultIsKeptWithThePendingRegistration() {
        var captor = ArgumentCaptor.forClass(PendingRegistration.class);

        service.handle(command());

        verify(pendingRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().getIdentityVerification().status())
                .isEqualTo(IdentityVerificationStatus.PARTIAL_MATCH);
    }

    /**
     * Un documento que ya tiene cuenta se rechaza antes de verificarlo.
     *
     * <p>Consultarlo gastaria una consulta de la cuota en un alta que de todos
     * modos iba a fallar al confirmar.</p>
     */
    @Test
    void aDocumentThatAlreadyHasAnAccountIsRejectedWithoutSpendingALookup() {
        when(userInformationRepository.existsByIdentityDocument(any())).thenReturn(true);

        assertThatThrownBy(() -> service.handle(command()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(identityVerificationService, never()).verify(any(), anyString(), anyString(), any());
        verify(smtpService, never()).sendSignUpVerificationEmailAsync(anyString(), anyString(), anyString());
    }
}
