package com.pocketpeers.backend.groups.domain.services;

import com.pocketpeers.backend.groups.domain.model.commands.AddGroupOperationCommand;
import com.pocketpeers.backend.groups.domain.model.commands.DeleteGroupOperationCommand;

public interface GroupOperationCommandService {
    Long handle(AddGroupOperationCommand command);
    void handle(DeleteGroupOperationCommand command);
}
