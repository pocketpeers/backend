package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreatePaymentResource;

public class CreatePaymentCommandFromResourceAssembler {
    public static CreatePaymentCommand toCommandFromResource(CreatePaymentResource resource) {
        return new CreatePaymentCommand(resource.description(), resource.amount(), resource.userId(), resource.expenseId());
    }
}
