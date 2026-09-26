package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import java.util.List;

public record PublicMemberProfileResource(
        Long userId,
        String fullName,
        String username,
        String photo,
        ReputationResource reputation,
        List<BadgeResource> badges,
        int completedPaymentsInGroup
) {
}
