package com.pocketpeers.backend.groups.domain.model.commands;

public record AddMemberCommand(Long groupId, Long userId ) {
    public AddMemberCommand {
        if (groupId == null || userId == null ) {
          throw new IllegalArgumentException( "Group, user, and requester IDs cannot be null." );
        }
    }
}