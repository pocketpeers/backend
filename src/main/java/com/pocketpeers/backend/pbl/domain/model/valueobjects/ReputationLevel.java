package com.pocketpeers.backend.pbl.domain.model.valueobjects;

public enum ReputationLevel {
    NEW("Nuevo", "Score en construccion con las primeras transacciones", 0),
    BRONZE("Bronce", "Cumplimiento financiero inicial y consistente", 25),
    SILVER("Plata", "Buen historial de pagos y responsabilidad", 60),
    GOLD("Oro", "Excelente reputacion financiera en grupos", 85);

    private final String displayName;
    private final String description;
    private final int minimumScore;

    ReputationLevel(String displayName, String description, int minimumScore) {
        this.displayName = displayName;
        this.description = description;
        this.minimumScore = minimumScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMinimumScore() {
        return minimumScore;
    }

    public static ReputationLevel fromScore(int score) {
        if (score >= GOLD.minimumScore) return GOLD;
        if (score >= SILVER.minimumScore) return SILVER;
        if (score >= BRONZE.minimumScore) return BRONZE;
        return NEW;
    }

    public static int pointsToNextLevel(int score) {
        for (ReputationLevel level : values()) {
            if (score < level.minimumScore) {
                return level.minimumScore - score;
            }
        }
        return 0;
    }
}
