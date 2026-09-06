package com.pocketpeers.backend.users.domain.exceptions;

/** El nombre de usuario ya esta registrado. */
public class UsernameAlreadyTakenException extends RuntimeException {
    public UsernameAlreadyTakenException(String username) {
        super("El usuario '" + username + "' ya esta registrado");
    }
}
