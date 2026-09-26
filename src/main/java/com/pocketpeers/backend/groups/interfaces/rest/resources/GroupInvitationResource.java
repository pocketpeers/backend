package com.pocketpeers.backend.groups.interfaces.rest.resources;

import java.time.OffsetDateTime;

public record GroupInvitationResource(
        Long id,
        Long groupId,
        String groupName,
        String groupPhoto,
        Long invitedUserId,
        String invitedUsername,
        String invitedFullName,
        String invitedPhoto,
        String invitedByUsername,
        String invitedByFullName,
        String status,
        // Con zona horaria: la fecha se guarda en la hora del servidor, y sin el
        // desfase el telefono la leeria como hora local.
        OffsetDateTime expiresAt
) {
}
