package com.pocketpeers.backend.shared.domain.exceptions;

/**
 * La peticion viene de una version de la aplicacion que ya no es compatible.
 *
 * <p>Se usa donde una version vieja no fallaria de forma evidente sino que
 * haria algo peor: quedarse sin poder unirse a grupos sin saber por que, o
 * registrarse sin identificador de telefono y agotar los topes de verificacion
 * de DNI de todos los demas. En vez de eso se le pide actualizar.</p>
 */
public class OutdatedClientException extends RuntimeException {

    public static final String CODE = "OUTDATED_CLIENT";

    /**
     * Version minima que el backend acepta, enviada por la aplicacion en la
     * cabecera {@link #HEADER}. Se sube cuando un cambio rompe a las versiones
     * anteriores.
     */
    public static final int MIN_SUPPORTED_APP_VERSION = 2;

    public static final String HEADER = "X-App-Version";

    public OutdatedClientException() {
        super("Hay una nueva versión de PocketPeers. Actualiza la app para continuar.");
    }

    /** Falla si la cabecera falta o trae una version anterior a la minima. */
    public static void requireSupported(String appVersionHeader) {
        int version;
        try {
            version = appVersionHeader == null ? 0 : Integer.parseInt(appVersionHeader.trim());
        } catch (NumberFormatException e) {
            version = 0;
        }
        if (version < MIN_SUPPORTED_APP_VERSION) {
            throw new OutdatedClientException();
        }
    }
}
