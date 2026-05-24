package com.pocketpeers.backend.groups.interfaces.rest.resources;

public record JoinGroupWithTokenResource(
        Long userId,
        String token
) {
}
