package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;

public record RegisterReputationEventResource(
        Long userId,
        Long groupId,
        Long paymentId,
        ReputationEventType type,
        String description
) {
}
