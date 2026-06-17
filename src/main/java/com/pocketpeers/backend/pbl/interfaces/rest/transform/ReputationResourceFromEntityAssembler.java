package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ReputationResource;

public class ReputationResourceFromEntityAssembler {
    public static ReputationResource toResourceFromEntity(UserReputation entity) {
        var level = entity.getLevel();
        return new ReputationResource(
                entity.getUser().getId(),
                entity.getScore(),
                level.getDisplayName(),
                level.getDescription(),
                entity.getPointsToNextLevel(),
                entity.getOnTimePaymentStreak(),
                entity.getCompletedPayments()
        );
    }
}
