package com.pocketpeers.backend.users.domain.exceptions;

/**
 * El nombre escrito en el registro no corresponde al DNI.
 *
 * <p>Se lanza antes de enviar el codigo al correo, para que la aplicacion pueda
 * pedir la correccion sin haber mandado nada.</p>
 */
public class IdentityMismatchException extends RuntimeException {

    public static final String CODE = "IDENTITY_MISMATCH";

    public IdentityMismatchException() {
        super("El DNI ingresado no coincide con el nombre ingresado. "
                + "Revisa que tu nombre y apellido estén escritos como en tu DNI.");
    }
}
