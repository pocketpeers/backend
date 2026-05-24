package com.pocketpeers.backend.users.interfaces.rest.transform;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.entities.Role;
import com.pocketpeers.backend.users.interfaces.rest.resources.UserResource;

public class UserResourceFromEntityAssembler {
    public static UserResource toResourceFromEntity(User user) {
        var roles = user.getRoles().stream().map(Role::getStringName).toList();
        return new UserResource(user.getId(), user.getUsername(), roles);
    }
}
