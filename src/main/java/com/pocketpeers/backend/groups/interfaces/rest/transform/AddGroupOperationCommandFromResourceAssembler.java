package com.pocketpeers.backend.groups.interfaces.rest.transform;

import com.pocketpeers.backend.groups.domain.model.commands.AddGroupOperationCommand;
import com.pocketpeers.backend.groups.interfaces.rest.resources.AddGroupOperationResource;

public class AddGroupOperationCommandFromResourceAssembler {
    public static AddGroupOperationCommand toCommandFromResource(AddGroupOperationResource resource) {
        return new AddGroupOperationCommand(resource.groupId(), resource.expenseId(), resource.paymentId());
    }
}
