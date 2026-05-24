package com.pocketpeers.backend.groups.domain.services;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.commands.*;

import java.util.Optional;

public interface GroupCommandService {

    Long handle(CreateGroupCommand command);
    Optional<Group> handle(UpdateGroupImageCommand command);
    Optional<Group> handle(UpdateGroupCommand command);
    void handle(DeleteGroupCommand command);
    String handle(GenerateInvitationCommand command);
}
