package com.pocketpeers.backend.users.interfaces.rest.resources;

/** Cuerpo del canje de codigo por contrasena nueva. */
public record ConfirmPasswordResetResource(String email, String code, String newPassword) {
}
