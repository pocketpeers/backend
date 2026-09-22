package com.pocketpeers.backend.users.domain.model.commands;

/**
 * Primer paso del alta: se guardan los datos y se envia un codigo al correo.
 *
 * <p>Lleva los mismos campos que {@link SignUpCommand} salvo los roles, que no
 * los elige quien se registra sino el sistema al confirmar.</p>
 */
public record RequestSignUpCommand(String username, String password, String firstName, String lastName,
                                   String phoneNumber, String photo, String email,
                                   String documentType, String documentNumber) {
}
