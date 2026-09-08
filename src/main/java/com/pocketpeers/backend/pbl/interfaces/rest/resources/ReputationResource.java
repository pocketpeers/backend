package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reputacion de un usuario.
 *
 * <p>Los siete primeros campos son los que el cliente movil ya consumia y no
 * cambian de nombre ni de tipo. Lo que cambia con PeerScore activo es de donde
 * salen: {@code score}, {@code level} y {@code pointsToNextLevel} pasan a venir
 * del calculo bayesiano en lugar del contador de puntos.</p>
 *
 * <p>{@code score} se conserva entero por compatibilidad y es el valor redondeado;
 * {@code peerScore} lleva el decimal exacto. El numero nunca deberia mostrarse
 * sin {@code band}: una banda ancha significa que el sistema todavia no sabe lo
 * suficiente, y un score sin ella hace que alguien con dos pagos parezca
 * comparable a alguien con doscientos.</p>
 *
 * @param peerScoreActive si el score y el nivel visibles vienen de PeerScore
 */
public record ReputationResource(
        Long userId,
        int score,
        String level,
        String levelDescription,
        int pointsToNextLevel,
        int onTimePaymentStreak,
        int completedPayments,
        Double peerScore,
        BandResource band,
        Double effectiveCounterparties,
        Integer distinctCounterparties,
        NextLevelResource nextLevel,
        List<ContributionResource> breakdown,
        LocalDateTime computedAt,
        String algoVersion,
        boolean peerScoreActive
) {

    /** Percentiles 5 y 95 de la estimacion: cuanta confianza merece el score. */
    public record BandResource(double low, double high) {
    }

    /**
     * Que falta para el siguiente nivel.
     *
     * <p>Los tres campos existen por separado porque el nivel exige las tres
     * condiciones a la vez, y sin distinguirlas la app solo puede mostrar un
     * numero en vez de decir que hacer.</p>
     */
    public record NextLevelResource(
            String level,
            String levelName,
            double missingScore,
            double missingCounterparties,
            boolean bandTooWide
    ) {
    }

    /**
     * Aporte de un bloque de eventos al score.
     *
     * <p>Es atribucion marginal, calculada por exclusion: cuanto cambiaria el
     * score si ese bloque no existiera. Los deltas no suman el total.</p>
     */
    public record ContributionResource(String label, double delta) {
    }
}
