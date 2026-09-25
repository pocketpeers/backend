package com.pocketpeers.backend.users.domain.model.commands;

import com.pocketpeers.backend.users.domain.model.valueobjects.SignUpOrigin;

/**
 * Primer paso del alta: se guardan los datos y se envia un codigo al correo.
 *
 * <p>Lleva los mismos campos que {@link SignUpCommand} salvo los roles, que no
 * los elige quien se registra sino el sistema al confirmar. El origen
 * —dispositivo e IP— sirve para aplicar los topes de verificacion de DNI.</p>
 */
public record RequestSignUpCommand(String username, String password, String firstName, String lastName,
                                   String phoneNumber, String photo, String email,
                                   String documentType, String documentNumber, SignUpOrigin origin) {

    /** Variante sin origen conocido, para quien no llega por HTTP. */
    public RequestSignUpCommand(String username, String password, String firstName, String lastName,
                                String phoneNumber, String photo, String email,
                                String documentType, String documentNumber) {
        this(username, password, firstName, lastName, phoneNumber, photo, email,
                documentType, documentNumber, SignUpOrigin.unknown());
    }
}
