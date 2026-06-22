package com.pocketpeers.backend.pbl.interfaces.rest.resources;

public record ReputationResource(
        Long userId,
        int score,
        String level,
        String levelDescription,
        int pointsToNextLevel,
        int onTimePaymentStreak,
        int completedPayments
) {
}
