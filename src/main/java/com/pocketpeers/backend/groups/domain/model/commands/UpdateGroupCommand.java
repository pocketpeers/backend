package com.pocketpeers.backend.groups.domain.model.commands;

public record UpdateGroupCommand(
        Long groupId,
        String name,
        String description

        ) {}