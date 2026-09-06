package com.pocketpeers.backend.users.interfaces.rest.resources;

/** Cuerpo del cambio de contrasena de un usuario autenticado. */
public record ChangePasswordResource(String currentPassword, String newPassword) {
}
