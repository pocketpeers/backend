package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.commands.UpdateExpenseCommand;
import com.pocketpeers.backend.operations.interfaces.rest.resources.UpdateExpenseResource;

public class UpdateExpenseCommandFromResourceAssembler {
    public static UpdateExpenseCommand toCommandFromResource(Long expenseId, UpdateExpenseResource resource,
                                                            String username) {
        return new UpdateExpenseCommand(expenseId, resource.name(), resource.amount(), resource.dueDate(),
                username);
    }
}
