package com.pocketpeers.backend.groups.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record InvitationToken(String token) {


    public InvitationToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de invitación no puede ser nulo ni vacío");
        }
    }

      public InvitationToken() {
        this(UUID.randomUUID().toString());
    }


    public String getToken() {
        return token;
    }
}
