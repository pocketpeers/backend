package com.pocketpeers.backend.groups.domain.services;

import com.pocketpeers.backend.groups.domain.model.commands.*;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;

import java.util.Optional;

public interface GroupMemberCommandService {
    Optional<GroupMember> handle(AddMemberCommand command);
    void handle (RemoveMemberCommand command);

    Optional<GroupMember> handle(JoinGroupWithTokenCommand command);
}
