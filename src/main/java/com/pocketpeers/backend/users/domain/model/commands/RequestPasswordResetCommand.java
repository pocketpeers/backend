package com.pocketpeers.backend.users.domain.model.commands;

/** Solicitud de codigo para recuperar una contrasena olvidada. */
public record RequestPasswordResetCommand(String email) {
}
