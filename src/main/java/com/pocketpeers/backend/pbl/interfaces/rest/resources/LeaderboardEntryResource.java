package com.pocketpeers.backend.pbl.interfaces.rest.resources;

/**
 * Una fila del ranking de un grupo.
 *
 * <p>Lleva la banda ademas del score, y no por completitud: un ranking sin
 * incertidumbre pone en la misma columna a quien tiene dos pagos y a quien
 * tiene doscientos, que es precisamente lo que el score con banda evita.</p>
 *
 * <p>A diferencia del perfil, estas filas se sirven del ultimo valor guardado y
 * no se recalculan por miembro: un grupo de veinte personas dispararia cuarenta
 * consultas por cada apertura del ranking. Quedan al dia por los eventos y por
 * la corrida nocturna.</p>
 */
public record LeaderboardEntryResource(
        Long userId,
        String fullName,
        String photo,
        int position,
        int score,
        String level,
        long unlockedBadges,
        boolean currentUser,
        String trend,
        Double peerScore,
        Double bandLow,
        Double bandHigh
) {
}
