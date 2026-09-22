package com.pocketpeers.backend.users.domain.exceptions;

/**
 * El codigo de recuperacion no sirve: no existe, ya vencio, ya se uso o se
 * agotaron los intentos.
 *
 * <p>Igual que con las credenciales, el mensaje no distingue entre esos casos
 * para no darle informacion util a quien este probando codigos al azar.</p>
 */
public class InvalidPasswordResetCodeException extends RuntimeException {
    public InvalidPasswordResetCodeException() {
        super("El código es incorrecto o ya venció");
    }
}
