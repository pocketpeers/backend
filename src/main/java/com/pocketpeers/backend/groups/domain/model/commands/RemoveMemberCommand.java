package com.pocketpeers.backend.groups.domain.model.commands;

public record RemoveMemberCommand(
        Long groupId,
        Long userId
) {}