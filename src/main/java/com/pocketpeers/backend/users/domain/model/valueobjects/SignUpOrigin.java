package com.pocketpeers.backend.users.domain.model.valueobjects;

/**
 * Desde donde llega una solicitud de alta, para aplicar los topes de
 * verificacion de DNI.
 *
 * @param deviceId identificador que manda la aplicacion en {@code X-Device-Id};
 *                 puede faltar si la peticion no viene de la app
 * @param clientIp IP de origen, ya resuelta detras del proxy
 */
public record SignUpOrigin(String deviceId, String clientIp) {

    public static SignUpOrigin unknown() {
        return new SignUpOrigin(null, null);
    }
}
