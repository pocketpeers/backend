package com.pocketpeers.backend.pbl.interfaces.rest.resources;

public record LeaderboardEntryResource(
        Long userId,
        String fullName,
        String photo,
        int position,
        int score,
        String level,
        long unlockedBadges,
        boolean currentUser,
        String trend
) {
}
