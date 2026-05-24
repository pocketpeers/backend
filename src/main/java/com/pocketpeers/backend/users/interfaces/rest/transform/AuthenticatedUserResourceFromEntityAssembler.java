package com.pocketpeers.backend.users.interfaces.rest.transform;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.interfaces.rest.resources.AuthenticatedUserResource;

public class AuthenticatedUserResourceFromEntityAssembler {
    public static AuthenticatedUserResource toResourceFromEntity(User user, String token) {
        return new AuthenticatedUserResource(user.getId(), user.getUsername(), token);
    }
}