package com.pocketpeers.backend.groups.interfaces.rest.transform;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupResource;

public class GroupResourceFromEntityAssembler {
    public static GroupResource toResourceFromEntity(Group group) {
        return new GroupResource(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getGroupPhoto(),
                group.getCreatedAt(),
                group.getUpdatedAt(),
                group.getAdminId()
        );   }
}
