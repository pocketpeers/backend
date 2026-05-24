package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.commands.DeleteUserCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignInCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignUpCommand;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Optional;

public interface UserCommandService {
    Optional<ImmutablePair<User, String>> handle(SignInCommand command);
    Optional<User> handle(SignUpCommand command);
    Optional<User> handle(DeleteUserCommand command);
}
