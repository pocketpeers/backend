package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import java.time.LocalDateTime;

public record BadgeResource(
        Long id,
        String code,
        String name,
        String description,
        boolean unlocked,
        LocalDateTime unlockedAt
) {
}
