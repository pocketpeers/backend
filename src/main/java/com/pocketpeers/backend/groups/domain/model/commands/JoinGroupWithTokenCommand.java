package com.pocketpeers.backend.groups.domain.model.commands;

public record JoinGroupWithTokenCommand(
        Long groupId,
        String token,       
        Long userId         
) {}
