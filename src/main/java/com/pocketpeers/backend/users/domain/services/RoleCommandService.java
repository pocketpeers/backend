package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.commands.SeedRolesCommand;

public interface RoleCommandService {
    void handle(SeedRolesCommand command);
}
