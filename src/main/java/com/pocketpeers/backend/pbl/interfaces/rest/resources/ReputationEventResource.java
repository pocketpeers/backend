package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;

import java.time.LocalDateTime;

public record ReputationEventResource(
        Long id,
        Long userId,
        Long groupId,
        Long paymentId,
        ReputationEventType type,
        int pointsDelta,
        int resultingScore,
        String description,
        LocalDateTime occurredAt
) {
}
