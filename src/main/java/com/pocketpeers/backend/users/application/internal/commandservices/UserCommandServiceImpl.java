package com.pocketpeers.backend.users.application.internal.commandservices;

import com.pocketpeers.backend.users.application.internal.identityverification.IdentityVerificationService;
import com.pocketpeers.backend.users.application.internal.outboundservices.hashing.HashingService;
import com.pocketpeers.backend.users.application.internal.outboundservices.tokens.TokenService;
import com.pocketpeers.backend.users.domain.exceptions.CurrentPasswordMismatchException;
import com.pocketpeers.backend.users.domain.exceptions.InvalidCredentialsException;
import com.pocketpeers.backend.users.domain.exceptions.InvalidPasswordResetCodeException;
import com.pocketpeers.backend.users.domain.exceptions.UsernameAlreadyTakenException;
import com.pocketpeers.backend.users.domain.exceptions.WeakPasswordException;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.commands.ChangePasswordCommand;
import com.pocketpeers.backend.users.domain.model.commands.ConfirmPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.CreateUserInformationCommand;
import com.pocketpeers.backend.users.domain.model.commands.DeleteUserCommand;
import com.pocketpeers.backend.users.domain.model.commands.RequestPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignInCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignUpCommand;
import com.pocketpeers.backend.users.domain.model.entities.PasswordResetCode;
import com.pocketpeers.backend.users.domain.model.entities.PendingRegistration;
import com.pocketpeers.backend.users.domain.exceptions.InvalidSignUpCodeException;
import com.pocketpeers.backend.users.domain.model.commands.ConfirmSignUpCommand;
import com.pocketpeers.backend.users.domain.model.commands.RequestSignUpCommand;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.PendingRegistrationRepository;
import com.pocketpeers.backend.users.domain.model.valueobjects.Roles;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByUserIdQuery;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityDocument;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerification;
import com.pocketpeers.backend.users.domain.model.valueobjects.EmailAddress;
import com.pocketpeers.backend.users.domain.model.valueobjects.PasswordPolicy;
import com.pocketpeers.backend.users.domain.services.SmtpService;
import com.pocketpeers.backend.users.domain.services.UserCommandService;
import com.pocketpeers.backend.users.domain.services.UserInformationCommandService;
import com.pocketpeers.backend.users.domain.services.UserInformationQueryService;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.PasswordResetCodeRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCommandServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final UserInformationCommandService userInformationCommandService;
    private final UserInformationQueryService userInformationQueryService;
    private final UserInformationRepository userInformationRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final SmtpService smtpService;
    private final IdentityVerificationService identityVerificationService;

    /**
     * Si el alta exige confirmar el correo antes de crear la cuenta.
     *
     * <p>Se deja configurable porque el publico objetivo de esta aplicacion
     * —personas no bancarizadas, muchas con poca practica digital— no siempre
     * tiene un correo que revise. Exigir la confirmacion protege los datos, pero
     * en un trabajo de campo puede dejar fuera a quien no puede abrir su bandeja
     * en ese momento. El interruptor permite decidirlo sin recompilar.</p>
     */
    @Value("${authentication.email-verification.enabled:true}")
    private boolean emailVerificationEnabled;

    private final RoleRepository roleRepository;

    public UserCommandServiceImpl(UserRepository userRepository, HashingService hashingService, TokenService tokenService,
                                  RoleRepository roleRepository, UserInformationCommandService userInformationCommandService,
                                  UserInformationQueryService userInformationQueryService,
                                  UserInformationRepository userInformationRepository,
                                  PasswordResetCodeRepository passwordResetCodeRepository,
                                  PendingRegistrationRepository pendingRegistrationRepository,
                                  SmtpService smtpService,
                                  IdentityVerificationService identityVerificationService) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.userInformationCommandService = userInformationCommandService;
        this.userInformationQueryService = userInformationQueryService;
        this.userInformationRepository = userInformationRepository;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.smtpService = smtpService;
        this.identityVerificationService = identityVerificationService;
    }

    /**
     * Handles the deletion of a user.
     * @param command DeleteUserCommand object containing the user's ID.
     * @return Optional<User> Deleted user.
     */
    @Override
    public Optional<User> handle(DeleteUserCommand command) {
        var user = userRepository.findById(command.userId());
        user.ifPresent(userRepository::delete);
        return user;
    }

    /**
     * Handle the sign-in command
     *
     * @param command the sign-in command containing the username and password
     * @return an optional containing the user matching the username and the generated token
     * @throws InvalidCredentialsException si el usuario no existe o la contrasena no coincide
     */
    @Override
    public Optional<ImmutablePair<User, String>> handle(SignInCommand command) {
        // El usuario inexistente y la contrasena incorrecta lanzan la misma
        // excepcion, con el mismo mensaje. Distinguirlos permitiria averiguar
        // que nombres de usuario estan registrados probandolos uno por uno.
        var user = userRepository.findByUsername(command.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!hashingService.matches(command.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        var query = new GetUserInformationByUserIdQuery(user.getId());
        var userInfo = userInformationQueryService.handle(query);
        if (userInfo.isEmpty())
            throw new RuntimeException("User information not found");
        // obtener rol
        var roles = user.getRoles();
        var role  = roles.isEmpty() ? "USER" : roles.stream().findFirst().get().getStringName();
        var token = tokenService.generateToken(user.getUsername(), role, userInfo.get().getFullName(),
                userInfo.get().getPhoneNumber(), userInfo.get().getPhoto(), userInfo.get().getEmail().email());
        return Optional.of(ImmutablePair.of(user, token));
    }

    /**
     * Handles user registration.
     * @param command SignUpCommand object containing the new user's details.
     * @return Optional<User> Created user.
     */
    @Override
    @Transactional
    public Optional<User> handle(SignUpCommand command) {
        return createAccount(command, null);
    }

    /**
     * Crea la cuenta y su perfil.
     *
     * @param identityVerification resultado de la verificacion del DNI, o nulo
     *                             si el alta no paso por ella
     */
    private Optional<User> createAccount(SignUpCommand command, IdentityVerification identityVerification) {
        if (userRepository.existsByUsername(command.username()))
            throw new UsernameAlreadyTakenException(command.username());
        // La contrasena se valida antes de crear nada: si no cumple la politica,
        // no debe quedar un usuario a medio registrar.
        requireStrongPassword(command.password());
        var roles = command.roles().stream().map(role -> roleRepository.findByName(role.getName()).orElseThrow(
                () -> new RuntimeException("Role not found")
        )).toList();
        // El documento se valida antes de crear el usuario, por el mismo motivo
        // que la contrasena: si el numero esta mal, no debe quedar una cuenta a
        // medio registrar que luego haya que limpiar a mano.
        var identityDocument = IdentityDocument.of(command.documentType(), command.documentNumber());

        var user = new User(command.username(), hashingService.encode(command.password()), roles);
        var savedUser = userRepository.save(user);
        CreateUserInformationCommand userInformationCommand = new CreateUserInformationCommand(command.firstName(), command.lastName(), command.phoneNumber(), command.photo(), command.email(), savedUser.getId(), identityDocument, identityVerification);
        userInformationCommandService.handle(userInformationCommand);
        return Optional.of(user);
    }

    // ------------------------------------------------------------------
    // Alta en dos pasos: verificacion del correo
    // ------------------------------------------------------------------

    /**
     * Primer paso: guarda el alta en espera y manda un codigo al correo.
     *
     * <p>Nada se crea todavia. El orden es lo importante: si la cuenta se
     * creara antes de comprobar el correo, una direccion mal escrita dejaria
     * una cuenta activa que su dueno no puede recuperar nunca, porque la
     * recuperacion de contrasena se apoya en ese mismo correo. Y una direccion
     * ajena dejaria una cuenta a nombre de alguien que no pidio nada.</p>
     *
     * <p>Aqui si se responde distinto cuando el usuario o el correo ya existen,
     * al contrario que en la recuperacion de contrasena. Es una diferencia
     * deliberada: un formulario de alta tiene que decir que ese nombre esta
     * tomado, porque si no la persona no puede completar el registro. La
     * informacion que se filtra es la misma que cualquiera obtendria probando a
     * registrarse.</p>
     *
     * @return true si se envio un codigo y hace falta confirmarlo; false si la
     *         verificacion esta desactivada y la cuenta ya quedo creada
     */
    @Override
    @Transactional
    public boolean handle(RequestSignUpCommand command) {
        var now = LocalDateTime.now();

        // Todo lo que puede rechazar el alta se comprueba antes de enviar nada.
        // Mandar un codigo y descubrir despues que el documento estaba mal
        // obligaria a repetir el ciclo entero por un dato que ya se conocia.
        if (userRepository.existsByUsername(command.username())) {
            throw new UsernameAlreadyTakenException(command.username());
        }
        if (userInformationRepository.findByEmail(new EmailAddress(command.email())).isPresent()) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo");
        }
        requireStrongPassword(command.password());
        var identityDocument = IdentityDocument.of(command.documentType(), command.documentNumber());
        // Antes solo se detectaba al confirmar el codigo. Adelantarlo evita
        // mandar un codigo que no va a servir y, sobre todo, gastar una consulta
        // de DNI de la cuota en un documento que ya tiene cuenta.
        if (userInformationRepository.existsByIdentityDocument(identityDocument)) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con ese documento de identidad");
        }

        // Un alta pendiente reserva el nombre de usuario mientras vive, para que
        // dos personas distintas no pidan el codigo con el mismo nombre y la
        // segunda en confirmar se estrelle despues de haberlo hecho todo bien.
        //
        // La reserva ignora el alta pendiente del propio correo: volver atras y
        // pedir otro codigo es lo normal, y antes esa solicitud chocaba contra
        // la anterior de uno mismo.
        if (pendingRegistrationRepository.existsUsablePendingForOtherEmail(
                command.username(), command.email(), now)) {
            throw new UsernameAlreadyTakenException(command.username());
        }

        // La verificacion del DNI va al final de las comprobaciones, despues de
        // todas las que no cuestan nada: cada una que falle antes es una consulta
        // de la cuota que no se gasta. Y va antes de enviar el correo, para que
        // un nombre que no coincide se corrija sin haber mandado ningun codigo.
        var identityVerification = identityVerificationService.verify(
                identityDocument, command.firstName(), command.lastName(), command.origin());

        if (!emailVerificationEnabled) {
            // Interruptor apagado: se crea la cuenta de una vez y se avisa a la
            // aplicacion para que no pida un codigo que nadie envio.
            LOGGER.info("Email verification disabled; creating the account without a code");
            createAccount(toSignUpCommand(command), identityVerification);
            // Tambien por este camino hay cuenta nueva, y tambien merece su
            // bienvenida: si solo se enviara tras confirmar el codigo, apagar la
            // verificacion dejaria a esos usuarios sin ninguna explicacion de
            // que hacer con la aplicacion.
            smtpService.sendWelcomeEmailAsync(command.email(), welcomeName(
                    command.firstName(), command.username()));
            return false;
        }

        // Pedir un codigo nuevo invalida el anterior: no deben quedar varios
        // vivos para el mismo correo.
        pendingRegistrationRepository.invalidatePendingFor(command.email(), now);

        var code = generateCode();
        var pending = new PendingRegistration(
                command.email(),
                command.username(),
                hashingService.encode(command.password()),
                command.firstName(),
                command.lastName(),
                command.phoneNumber(),
                command.photo(),
                command.documentType(),
                command.documentNumber(),
                hashingService.encode(code),
                now);
        pending.recordIdentityVerification(identityVerification);
        pendingRegistrationRepository.save(pending);

        smtpService.sendSignUpVerificationEmailAsync(
                command.email(),
                command.firstName() == null || command.firstName().isBlank()
                        ? command.username()
                        : command.firstName(),
                code);
        return true;
    }

    /**
     * Segundo paso: canjea el codigo y crea la cuenta de verdad.
     *
     * <p>Cada intento fallido se cuenta y se persiste, para que un codigo de
     * seis digitos no se pueda adivinar probando combinaciones.</p>
     */
    @Override
    @Transactional
    public Optional<User> handle(ConfirmSignUpCommand command) {
        var now = LocalDateTime.now();
        var pending = pendingRegistrationRepository
                .findFirstByEmailOrderByIdDesc(command.email())
                .orElseThrow(InvalidSignUpCodeException::new);

        if (!pending.isUsable(now)) {
            throw new InvalidSignUpCodeException();
        }

        if (!hashingService.matches(command.code(), pending.getCodeHash())) {
            pending.registerFailedAttempt();
            pendingRegistrationRepository.save(pending);
            throw new InvalidSignUpCodeException();
        }

        // El nombre pudo ocuparse entre la peticion y la confirmacion: la
        // reserva solo cubre a otros registros pendientes, no a un alta directa
        // ni a una hecha antes de que existiera este flujo.
        if (userRepository.existsByUsername(pending.getUsername())) {
            throw new UsernameAlreadyTakenException(pending.getUsername());
        }

        var roles = List.of(roleRepository.findByName(Roles.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role not found")));
        var identityDocument = IdentityDocument.of(pending.getDocumentType(), pending.getDocumentNumber());

        // La contrasena ya viaja cifrada desde el primer paso, asi que se pasa
        // tal cual: volver a cifrarla produciria un hash de un hash y nadie
        // podria iniciar sesion.
        var user = userRepository.save(new User(pending.getUsername(), pending.getPasswordHash(), roles));
        userInformationCommandService.handle(new CreateUserInformationCommand(
                pending.getFirstName(), pending.getLastName(), pending.getPhoneNumber(),
                pending.getPhoto(), pending.getEmail(), user.getId(), identityDocument,
                pending.getIdentityVerification()));

        pending.markUsed(now);
        pendingRegistrationRepository.save(pending);

        // La bienvenida sale ahora, con la cuenta ya creada, y en segundo plano:
        // un correo de cortesia no debe poder retrasar ni tumbar la respuesta de
        // un registro que si se completo.
        smtpService.sendWelcomeEmailAsync(pending.getEmail(), welcomeName(
                pending.getFirstName(), pending.getUsername()));

        LOGGER.info("Sign-up confirmed and account created. userId={}", user.getId());
        return Optional.of(user);
    }

    /**
     * Con que nombre saludar.
     *
     * <p>El nombre de pila si lo hay, y el usuario si no. Un correo que empieza
     * con "Hola ," por un campo vacio se lee como generado por una maquina
     * rota, que es justo lo contrario de lo que busca una bienvenida.</p>
     */
    private String welcomeName(String firstName, String username) {
        return firstName == null || firstName.isBlank() ? username : firstName;
    }

    private SignUpCommand toSignUpCommand(RequestSignUpCommand command) {
        var roles = List.of(roleRepository.findByName(Roles.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role not found")));
        return new SignUpCommand(command.username(), command.password(), roles,
                command.firstName(), command.lastName(), command.phoneNumber(),
                command.photo(), command.email(), command.documentType(), command.documentNumber());
    }

    // ------------------------------------------------------------------
    // Recuperacion de contrasena
    // ------------------------------------------------------------------

    /**
     * Emite un codigo de seis digitos y lo envia por correo.
     *
     * <p>Termina en silencio cuando el correo no corresponde a ninguna cuenta.
     * Responder distinto en ese caso convertiria este endpoint en una forma de
     * averiguar que correos estan registrados.</p>
     */
    @Override
    @Transactional
    public void handle(RequestPasswordResetCommand command) {
        var now = LocalDateTime.now();
        var information = userInformationRepository.findByEmail(new EmailAddress(command.email()));
        if (information.isEmpty()) {
            LOGGER.info("Password reset requested for an unknown email; responding as if it existed");
            return;
        }

        var user = userRepository.findById(information.get().getUser().getId());
        if (user.isEmpty()) {
            return;
        }

        // Pedir un codigo nuevo invalida el anterior: no deben quedar varios
        // codigos vivos a la vez para la misma cuenta.
        passwordResetCodeRepository.invalidatePendingCodes(user.get().getId(), now);

        var code = generateCode();
        passwordResetCodeRepository.save(
                new PasswordResetCode(user.get().getId(), hashingService.encode(code), now));

        // Envio en segundo plano: el resultado no cambia la respuesta, asi que
        // esperarlo solo haria que la pantalla se quede girando. Los fallos se
        // registran dentro del propio servicio de correo.
        smtpService.sendPasswordResetEmailAsync(
                command.email(), information.get().getFullName(), code);
    }

    /**
     * Canjea el codigo por una contrasena nueva.
     *
     * <p>Cada intento fallido se contabiliza y se persiste, de modo que un codigo
     * de seis digitos no se pueda adivinar probando combinaciones.</p>
     */
    @Override
    @Transactional
    public void handle(ConfirmPasswordResetCommand command) {
        requireStrongPassword(command.newPassword());

        var now = LocalDateTime.now();
        var information = userInformationRepository.findByEmail(new EmailAddress(command.email()));
        if (information.isEmpty()) {
            throw new InvalidPasswordResetCodeException();
        }

        var userId = information.get().getUser().getId();
        var resetCode = passwordResetCodeRepository.findFirstByUserIdOrderByIdDesc(userId)
                .orElseThrow(InvalidPasswordResetCodeException::new);

        if (!resetCode.isUsable(now)) {
            throw new InvalidPasswordResetCodeException();
        }

        if (!hashingService.matches(command.code(), resetCode.getCodeHash())) {
            resetCode.registerFailedAttempt();
            passwordResetCodeRepository.save(resetCode);
            throw new InvalidPasswordResetCodeException();
        }

        var user = userRepository.findById(userId).orElseThrow(InvalidPasswordResetCodeException::new);
        user.setPassword(hashingService.encode(command.newPassword()));
        userRepository.save(user);

        resetCode.markUsed(now);
        passwordResetCodeRepository.save(resetCode);

        LOGGER.info("Password reset completed. userId={}", userId);
    }

    /**
     * Cambia la contrasena de alguien que ya inicio sesion.
     *
     * <p>Se exige la contrasena actual aunque la peticion venga autenticada: un
     * telefono desbloqueado no deberia alcanzar para tomar control de la cuenta.</p>
     */
    @Override
    @Transactional
    public void handle(ChangePasswordCommand command) {
        var user = userRepository.findByUsername(command.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!hashingService.matches(command.currentPassword(), user.getPassword())) {
            // No es InvalidCredentialsException: la sesion es valida y lo que
            // fallo es un campo del formulario. Un 401 aqui haria que la app
            // cerrara la sesion por escribir mal la clave actual.
            throw new CurrentPasswordMismatchException();
        }

        requireStrongPassword(command.newPassword());
        if (hashingService.matches(command.newPassword(), user.getPassword())) {
            throw new WeakPasswordException("La contraseña nueva debe ser distinta de la actual");
        }

        user.setPassword(hashingService.encode(command.newPassword()));
        userRepository.save(user);

        // Cualquier codigo de recuperacion pendiente deja de tener sentido.
        passwordResetCodeRepository.invalidatePendingCodes(user.getId(), LocalDateTime.now());

        LOGGER.info("Password changed. userId={}", user.getId());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private void requireStrongPassword(String password) {
        var error = PasswordPolicy.validationError(password);
        if (error != null) {
            throw new WeakPasswordException(error);
        }
    }

    /**
     * Codigo de seis digitos, con ceros a la izquierda incluidos.
     *
     * <p>Se usa {@link SecureRandom} y no {@code Math.random()}: un generador
     * predecible haria adivinable el codigo sin necesidad de probarlo.</p>
     */
    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }
}
