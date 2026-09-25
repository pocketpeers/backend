package com.pocketpeers.backend.users.domain.exceptions;

/**
 * El dispositivo o la red agotaron los intentos de verificacion del dia.
 *
 * <p>Existe porque cada consulta al servicio externo sale de una cuota mensual
 * fija. Sin tope, bastaria probar documentos al azar para dejar sin
 * verificacion al resto de participantes.</p>
 */
public class IdentityLookupLimitException extends RuntimeException {

    public static final String CODE = "IDENTITY_LOOKUP_LIMIT";

    public IdentityLookupLimitException() {
        super("Alcanzaste el límite de verificaciones por hoy. Intenta nuevamente mañana.");
    }
}
