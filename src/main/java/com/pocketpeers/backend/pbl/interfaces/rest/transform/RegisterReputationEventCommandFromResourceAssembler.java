package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.RegisterReputationEventResource;

public class RegisterReputationEventCommandFromResourceAssembler {
    public static RegisterReputationEventCommand toCommandFromResource(RegisterReputationEventResource resource) {
        return new RegisterReputationEventCommand(
                resource.userId(),
                resource.groupId(),
                resource.paymentId(),
                resource.type(),
                resource.description(),
                resource.counterpartyId(),
                resource.amount(),
                resource.dueAt(),
                resource.resolvedAt()
        );
    }
}
