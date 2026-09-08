package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStatsSnapshot;

/**
 * Provee las estadisticas de referencia que el calculador usa como escala y como prior.
 */
public interface GroupStatsProvider {

    /** Foto vigente de las estadisticas, posiblemente cacheada. */
    GroupStatsSnapshot snapshot();

    /** Fuerza que la proxima foto se recalcule. */
    void invalidate();
}
