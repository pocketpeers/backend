package com.pocketpeers.backend.users.interfaces.acl;

import com.pocketpeers.backend.users.domain.model.commands.SignUpCommand;
import com.pocketpeers.backend.users.domain.model.entities.Role;
import com.pocketpeers.backend.users.domain.model.queries.GetUserByIdQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserByUsernameQuery;
import com.pocketpeers.backend.users.domain.services.UserCommandService;
import com.pocketpeers.backend.users.domain.services.UserQueryService;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;

public class UserContextFacade {
    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    public UserContextFacade(UserCommandService userCommandService, UserQueryService userQueryService) {
        this.userCommandService = userCommandService;
        this.userQueryService = userQueryService;
    }

    /**
     * Fetches the id of the user with the given username.
     * @param username The username of the user.
     * @return The id of the user.
     */
    public Long fetchUserIdByUsername(String username) {
        var getUserByUsernameQuery = new GetUserByUsernameQuery(username);
        var result = userQueryService.handle(getUserByUsernameQuery);
        if (result.isEmpty()) return 0L;
        return result.get().getId();
    }

    /**
     * Fetches the username of the user with the given id.
     * @param userId The id of the user.
     * @return The username of the user.
     */
    public String fetchUsernameByUserId(Long userId) {
        var getUserByIdQuery = new GetUserByIdQuery(userId);
        var result = userQueryService.handle(getUserByIdQuery);
        if (result.isEmpty()) return Strings.EMPTY;
        return result.get().getUsername();
    }

}