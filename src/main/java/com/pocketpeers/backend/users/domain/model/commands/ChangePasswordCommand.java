package com.pocketpeers.backend.users.domain.model.commands;

/**
 * Cambio de contrasena de un usuario que ya inicio sesion.
 *
 * <p>Exige la contrasena actual aunque la sesion este autenticada: si alguien
 * deja el telefono desbloqueado, no deberia poder cambiar la clave sin conocerla.</p>
 */
public record ChangePasswordCommand(String username, String currentPassword, String newPassword) {
}
