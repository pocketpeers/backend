package com.pocketpeers.backend.users.application.internal.commandservices;

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
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByUserIdQuery;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityDocument;
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
    private final SmtpService smtpService;

    private final RoleRepository roleRepository;

    public UserCommandServiceImpl(UserRepository userRepository, HashingService hashingService, TokenService tokenService,
                                  RoleRepository roleRepository, UserInformationCommandService userInformationCommandService,
                                  UserInformationQueryService userInformationQueryService,
                                  UserInformationRepository userInformationRepository,
                                  PasswordResetCodeRepository passwordResetCodeRepository,
                                  SmtpService smtpService) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.userInformationCommandService = userInformationCommandService;
        this.userInformationQueryService = userInformationQueryService;
        this.userInformationRepository = userInformationRepository;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.smtpService = smtpService;
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
        CreateUserInformationCommand userInformationCommand = new CreateUserInformationCommand(command.firstName(), command.lastName(), command.phoneNumber(), command.photo(), command.email(), savedUser.getId(), identityDocument);
        userInformationCommandService.handle(userInformationCommand);
        return Optional.of(user);
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
