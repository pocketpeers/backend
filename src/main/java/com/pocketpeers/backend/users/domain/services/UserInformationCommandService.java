package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.domain.model.commands.CreateUserInformationCommand;
import com.pocketpeers.backend.users.domain.model.commands.DeleteUserInformationCommand;
import com.pocketpeers.backend.users.domain.model.commands.UpdateUserInformationCommand;

import java.util.Optional;

public interface UserInformationCommandService {
    Optional<UserInformation> handle(CreateUserInformationCommand command);
    Optional<UserInformation> handle(DeleteUserInformationCommand command);
    Optional<UserInformation> handle(UpdateUserInformationCommand command);
}
