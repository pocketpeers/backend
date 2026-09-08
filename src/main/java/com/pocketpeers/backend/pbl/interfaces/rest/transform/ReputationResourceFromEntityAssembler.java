package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.NextLevelGoal;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ReputationResource;

import java.util.List;

public class ReputationResourceFromEntityAssembler {

    /**
     * Arma la vista de reputacion a partir del agregado y del calculo fresco.
     *
     * <p>Recibe {@code peerScoreActive} en vez de leer la configuracion porque
     * es una clase estatica sin inyeccion; quien la llama ya sabe si el motor
     * nuevo esta activo.</p>
     *
     * @param result resultado recien calculado, o null si no se pudo calcular
     */
    public static ReputationResource toResourceFromEntity(UserReputation entity, ScoreResult result,
                                                          NextLevelGoal goal, boolean peerScoreActive) {
        boolean usePeerScore = peerScoreActive && result != null;

        // El nivel de PeerScore no se deriva del puntaje: exige tambien certeza y
        // diversidad de contrapartes, asi que se toma ya resuelto del resultado.
        var level = usePeerScore ? result.level() : entity.getLevel();

        // Se redondea en vez de truncar para no restar casi un punto entero al
        // 99% de los usuarios, que es lo que hace un cast a int.
        int visibleScore = usePeerScore ? (int) Math.round(result.score()) : entity.getScore();

        // Hacia arriba: si faltan 0.2 puntos, faltan puntos, y mostrar 0 diria
        // que el nivel ya se alcanzo.
        int pointsToNextLevel = usePeerScore
                ? (goal == null ? 0 : (int) Math.ceil(goal.missingScore()))
                : entity.getPointsToNextLevel();

        return new ReputationResource(
                entity.getUser().getId(),
                visibleScore,
                level.getDisplayName(),
                level.getDescription(),
                pointsToNextLevel,
                entity.getOnTimePaymentStreak(),
                entity.getCompletedPayments(),
                result == null ? null : result.score(),
                result == null ? null : new ReputationResource.BandResource(result.bandLow(), result.bandHigh()),
                result == null ? null : result.effectiveCounterparties(),
                result == null ? null : result.distinctCounterparties(),
                nextLevel(goal),
                breakdown(result),
                entity.getComputedAt(),
                entity.getAlgoVersion(),
                usePeerScore);
    }

    private static ReputationResource.NextLevelResource nextLevel(NextLevelGoal goal) {
        if (goal == null) {
            // Nivel maximo alcanzado: no hay siguiente objetivo que describir.
            return null;
        }
        return new ReputationResource.NextLevelResource(
                goal.level().name(),
                goal.level().getDisplayName(),
                goal.missingScore(),
                goal.missingCounterparties(),
                goal.bandTooWide());
    }

    private static List<ReputationResource.ContributionResource> breakdown(ScoreResult result) {
        if (result == null) {
            return List.of();
        }
        return result.breakdown().stream()
                .map(contribution -> new ReputationResource.ContributionResource(
                        contribution.label(), contribution.delta()))
                .toList();
    }
}
