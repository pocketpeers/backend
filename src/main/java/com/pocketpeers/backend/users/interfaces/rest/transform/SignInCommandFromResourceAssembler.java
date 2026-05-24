package com.pocketpeers.backend.users.interfaces.rest.transform;

import com.pocketpeers.backend.users.domain.model.commands.SignInCommand;
import com.pocketpeers.backend.users.interfaces.rest.resources.SignInResource;

public class SignInCommandFromResourceAssembler {
    public static SignInCommand toCommandFromResource(SignInResource signInResource) {
        return new SignInCommand(signInResource.username(), signInResource.password());
    }
}