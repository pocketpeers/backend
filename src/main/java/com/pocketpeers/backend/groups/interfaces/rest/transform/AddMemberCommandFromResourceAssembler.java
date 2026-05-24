package com.pocketpeers.backend.groups.interfaces.rest.transform;

import com.pocketpeers.backend.groups.domain.model.commands.AddMemberCommand;
import com.pocketpeers.backend.groups.interfaces.rest.resources.AddMemberResource;

public class AddMemberCommandFromResourceAssembler {
    public static AddMemberCommand toCommandFromResource(AddMemberResource addMemberResource) {
        return new AddMemberCommand(addMemberResource.groupId(), addMemberResource.userId());
    }
}



