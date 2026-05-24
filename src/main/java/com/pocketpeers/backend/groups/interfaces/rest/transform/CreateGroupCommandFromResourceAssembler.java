package com.pocketpeers.backend.groups.interfaces.rest.transform;

import com.pocketpeers.backend.groups.domain.model.commands.CreateGroupCommand;
import com.pocketpeers.backend.groups.interfaces.rest.resources.CreateGroupResource;

public class CreateGroupCommandFromResourceAssembler {
    public static CreateGroupCommand toCommandFromResource(CreateGroupResource createGroupResource) {
        return new CreateGroupCommand(createGroupResource.name(), createGroupResource.groupPhoto(), createGroupResource.description(), createGroupResource.adminId());
    }
}
