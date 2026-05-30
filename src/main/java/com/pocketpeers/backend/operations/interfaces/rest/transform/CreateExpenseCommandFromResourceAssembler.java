package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.commands.CreateExpenseCommand;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreateExpenseResource;

public class CreateExpenseCommandFromResourceAssembler {
    public static CreateExpenseCommand toCommandFromResource(CreateExpenseResource resource) {
        return new CreateExpenseCommand(resource.name(), resource.amount(), resource.userId(), resource.groupId(), resource.dueDate());
    }
}
