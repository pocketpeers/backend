package com.pocketpeers.backend.users.domain.model.commands;

import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityDocument;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerification;

public record CreateUserInformationCommand(String firstName, String lastName, String phoneNumber, String photo,
                                           String email, Long userId, IdentityDocument identityDocument,
                                           IdentityVerification identityVerification) {

    /** Variante sin resultado de verificacion, para las altas que no pasan por ella. */
    public CreateUserInformationCommand(String firstName, String lastName, String phoneNumber, String photo,
                                        String email, Long userId, IdentityDocument identityDocument) {
        this(firstName, lastName, phoneNumber, photo, email, userId, identityDocument, null);
    }

    /**
     * Variante sin documento, para las rutas que crean un perfil fuera del
     * registro (por ejemplo la fachada que usan otros contextos). El documento
     * se exige en el registro, que es donde entra una persona nueva al estudio.
     */
    public CreateUserInformationCommand(String firstName, String lastName, String phoneNumber, String photo,
                                        String email, Long userId) {
        this(firstName, lastName, phoneNumber, photo, email, userId, null, null);
    }
}
