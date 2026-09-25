package com.pocketpeers.backend.users.domain.exceptions;

/**
 * El servicio externo de consulta de DNI no pudo responder: caido, sin token,
 * cuota agotada o tiempo de espera vencido.
 *
 * <p>Nunca llega al usuario. Quien la atrapa deja la cuenta como pendiente de
 * verificar y sigue con el registro.</p>
 */
public class DniLookupUnavailableException extends RuntimeException {

    public DniLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
