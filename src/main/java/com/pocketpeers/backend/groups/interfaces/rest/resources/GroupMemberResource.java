package com.pocketpeers.backend.groups.interfaces.rest.resources;

import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;

public record GroupMemberResource(
        Long groupId,
        Long userId,
        String fullName,
        String photo,
        GroupRole role,
        java.util.Date joinedAt
) {
}
