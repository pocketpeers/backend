package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import java.time.LocalDateTime;

/**
 * Un punto de la linea de evolucion del score.
 *
 * <p>Viaja con la banda y el nivel, y no solo con el puntaje, para que el cliente
 * pueda dibujar la incertidumbre en vez de una linea que aparenta ser exacta.</p>
 */
public record ScoreSeriesPointResource(
        LocalDateTime at,
        double score,
        double bandLow,
        double bandHigh,
        String level,
        String levelName
) {
}
