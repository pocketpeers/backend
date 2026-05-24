package com.pocketpeers.backend.groups.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * Record para el token de invitación del grupo
 * Garantiza un valor único y válido para el token
 * Se utiliza para invitar a nuevos miembros al grupo
 * El token se genera automáticamente si no se proporciona
 * El token no puede ser nulo ni vacío
 * El token es inmutable
 * El token es un valor embebido en la entidad Grupo
 *
 * @author Fiorella Jarama Peñaloza
 */
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
