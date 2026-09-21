package com.pocketpeers.backend.users.interfaces.rest.resources;

import java.util.List;

public record SignUpResource(String username,
                             String password,
                             List<String> roles,
                             String firstName,
                             String lastName,
                             String phoneNumber,
                             String photo,
                             String email,
                             /** DNI, CE o PASAPORTE. */
                             String documentType,
                             String documentNumber) {
}
