package com.pocketpeers.backend.groups.interfaces.rest.transform;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupMemberResource;

public class CreateGroupMemberCommandFromResourceAssembler {
    public static GroupMemberResource fromCommandToResource(GroupMember resource) {
        var userInformation = resource.getUser().getUserInformation();
        return new GroupMemberResource(
                resource.getGroup().getId(),
                resource.getUser().getId(),
                userInformation.getFullName(),
                userInformation.getPhoto(),
                resource.getRole(),
                resource.getJoinedAt()
        );
    }
}
