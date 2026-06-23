package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ReputationEventResource;

public class ReputationEventResourceFromEntityAssembler {
    public static ReputationEventResource toResourceFromEntity(ReputationEvent entity) {
        return new ReputationEventResource(
                entity.getId(),
                entity.getUser().getId(),
                entity.getGroupId(),
                entity.getPaymentId(),
                entity.getType(),
                entity.getPointsDelta(),
                entity.getResultingScore(),
                entity.getDescription(),
                entity.getOccurredAt()
        );
    }
}
