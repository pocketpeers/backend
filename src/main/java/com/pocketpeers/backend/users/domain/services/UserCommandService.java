package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.commands.ChangePasswordCommand;
import com.pocketpeers.backend.users.domain.model.commands.ConfirmPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.DeleteUserCommand;
import com.pocketpeers.backend.users.domain.model.commands.RequestPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignInCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignUpCommand;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Optional;

public interface UserCommandService {
    Optional<ImmutablePair<User, String>> handle(SignInCommand command);
    Optional<User> handle(SignUpCommand command);
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
