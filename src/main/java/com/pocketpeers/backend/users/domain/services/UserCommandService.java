package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.commands.ChangePasswordCommand;
import com.pocketpeers.backend.users.domain.model.commands.ConfirmPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.ConfirmSignUpCommand;
import com.pocketpeers.backend.users.domain.model.commands.RequestSignUpCommand;
import com.pocketpeers.backend.users.domain.model.commands.DeleteUserCommand;
import com.pocketpeers.backend.users.domain.model.commands.RequestPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignInCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignUpCommand;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Optional;

public interface UserCommandService {
    Optional<ImmutablePair<User, String>> handle(SignInCommand command);
    Optional<User> handle(SignUpCommand command);

    /**
     * Primer paso del alta: guarda los datos y envia el codigo al correo.
     *
     * <p>No crea nada todavia. Si la cuenta se creara antes de comprobar el
     * correo, una direccion mal escrita dejaria una cuenta que su dueno no puede
     * recuperar, porque la recuperacion se apoya justo en ese correo.</p>
     *
     * @return true si hay que confirmar con un codigo; false si la verificacion
     *         esta desactivada y la cuenta ya quedo creada
     */
    boolean handle(RequestSignUpCommand command);

    /** Segundo paso del alta: canjea el codigo y crea la cuenta. */
    Optional<User> handle(ConfirmSignUpCommand command);
    Optional<User> handle(DeleteUserCommand command);

    /**
     * Emite un codigo de recuperacion y lo envia por correo.
     *
     * <p>No devuelve nada y no falla cuando el correo no esta registrado. Es
     * deliberado: cualquier diferencia observable entre "existe" y "no existe"
     * convierte este endpoint en un buscador de cuentas.</p>
     */
    void handle(RequestPasswordResetCommand command);

    /** Canjea el codigo por una contrasena nueva. */
    void handle(ConfirmPasswordResetCommand command);

    /** Cambia la contrasena de un usuario autenticado que conoce la actual. */
    void handle(ChangePasswordCommand command);
}
