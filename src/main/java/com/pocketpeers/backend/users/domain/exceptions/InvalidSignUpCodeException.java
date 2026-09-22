package com.pocketpeers.backend.users.domain.exceptions;

/**
 * El codigo de verificacion del correo no sirve: no existe, ya vencio, ya se
 * uso o se agotaron los intentos.
 *
 * <p>Como en la recuperacion de contrasena, el mensaje no distingue entre esos
 * casos: hacerlo le diria a quien prueba codigos al azar cuanto le falta.</p>
 */
public class InvalidSignUpCodeException extends RuntimeException {
    public InvalidSignUpCodeException() {
        super("El código es incorrecto o ya venció");
    }
}
