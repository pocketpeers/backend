package com.pocketpeers.backend.pbl.domain.model.commands;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;

public record RegisterReputationEventCommand(
        Long userId,
        Long groupId,
        Long paymentId,
        ReputationEventType type,
        String description
) {
}
