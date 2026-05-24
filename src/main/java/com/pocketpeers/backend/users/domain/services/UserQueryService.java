package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.queries.GetAllUsersQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserByIdQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserByUsernameQuery;

import java.util.List;
import java.util.Optional;

public interface UserQueryService {
    List<User> handle(GetAllUsersQuery query);
    Optional<User> handle(GetUserByIdQuery query);
    Optional<User> handle(GetUserByUsernameQuery query);
}

