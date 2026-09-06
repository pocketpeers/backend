package com.pocketpeers.backend.users.domain.model.commands;

/** Canje del codigo recibido por correo por una contrasena nueva. */
public record ConfirmPasswordResetCommand(String email, String code, String newPassword) {
}
