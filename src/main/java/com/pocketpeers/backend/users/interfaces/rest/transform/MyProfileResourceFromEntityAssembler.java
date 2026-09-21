package com.pocketpeers.backend.users.interfaces.rest.transform;

import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.interfaces.rest.resources.MyProfileResource;

public class MyProfileResourceFromEntityAssembler {
    public static MyProfileResource toResourceFromEntity(UserInformation userInformation) {
        var document = userInformation.getIdentityDocument();
        return new MyProfileResource(
                userInformation.getId(),
                userInformation.getUser().getUsername(),
                userInformation.getFullName(),
                userInformation.getPhoneNumber(),
                userInformation.getPhoto(),
                userInformation.getEmailAddress(),
                userInformation.getUser().getId(),
                document != null ? document.type().name() : null,
                document != null ? document.number() : null
        );
    }
}
