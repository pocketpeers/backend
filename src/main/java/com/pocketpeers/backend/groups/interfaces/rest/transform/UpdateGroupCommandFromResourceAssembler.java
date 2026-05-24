package com.pocketpeers.backend.groups.interfaces.rest.transform;

import com.pocketpeers.backend.groups.domain.model.commands.UpdateGroupCommand;
import com.pocketpeers.backend.groups.interfaces.rest.resources.UpdateGroupResource;

public class UpdateGroupCommandFromResourceAssembler {
    public static UpdateGroupCommand toCommandFromResource(Long groupId, UpdateGroupResource resource) {
        return new UpdateGroupCommand(groupId, resource.name(), resource.description());
    }
}
