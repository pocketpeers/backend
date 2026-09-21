package com.pocketpeers.backend.users.domain.model.commands;

import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityDocument;

public record CreateUserInformationCommand(String firstName, String lastName, String phoneNumber, String photo,
                                           String email, Long userId, IdentityDocument identityDocument) {

    /**
     * Variante sin documento, para las rutas que crean un perfil fuera del
     * registro (por ejemplo la fachada que usan otros contextos). El documento
     * se exige en el registro, que es donde entra una persona nueva al estudio.
     */
    public CreateUserInformationCommand(String firstName, String lastName, String phoneNumber, String photo,
                                        String email, Long userId) {
        this(firstName, lastName, phoneNumber, photo, email, userId, null);
    }
}
