package com.pocketpeers.backend.users.domain.exceptions;

/**
 * Credenciales de inicio de sesion incorrectas.
 *
 * <p>El mensaje es intencionalmente el mismo tanto si el usuario no existe como
 * si la contrasena no coincide. Distinguirlos permitiria averiguar que usuarios
 * estan registrados probando nombres uno por uno.</p>
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Usuario o contraseña incorrectos");
    }
}
