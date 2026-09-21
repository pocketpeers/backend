package com.pocketpeers.backend.pbl.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * El score de un usuario tal como habria sido en un instante del pasado.
 *
 * <p>No es un valor guardado: se reconstruye recalculando PeerScore con la
 * evidencia que ya existia en ese momento. Esa es la unica forma honesta de
 * dibujar una linea temporal de un score que no acumula. El motor PBL anterior
 * si guardaba un {@code resultingScore} por evento, pero ese numero pertenece a
 * otro algoritmo: graficarlo junto al score que hoy ve el usuario producia dos
 * historias distintas del mismo historial.</p>
 *
 * <p>Lleva la banda ademas del puntaje porque una linea sin ella afirmaria una
 * precision que el score no tiene, sobre todo en los primeros puntos, donde la
 * estimacion se apoya casi entera en el prior.</p>
 *
 * @param at       instante al que corresponde el corte
 * @param score    puntaje reconstruido en [0, 100]
 * @param bandLow  percentil 5 en ese corte
 * @param bandHigh percentil 95 en ese corte
 * @param level    nivel que correspondia en ese corte
 */
public record ScoreSeriesPoint(
        LocalDateTime at,
        double score,
        double bandLow,
        double bandHigh,
        ReputationLevel level
) {

    public ScoreSeriesPoint {
        if (at == null) {
            throw new IllegalArgumentException("at es obligatorio");
        }
        if (level == null) {
            throw new IllegalArgumentException("level es obligatorio");
        }
    }

    public static ScoreSeriesPoint from(LocalDateTime at, ScoreResult result) {
        return new ScoreSeriesPoint(at, result.score(), result.bandLow(), result.bandHigh(), result.level());
    }
}
