package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreSeriesPoint;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ScoreSeriesPointResource;

public class ScoreSeriesPointResourceFromEntityAssembler {

    public static ScoreSeriesPointResource toResourceFromEntity(ScoreSeriesPoint point) {
        // Viajan el nombre del enum y el nombre visible: el primero para que el
        // cliente pueda comparar niveles sin traducir cadenas, el segundo para
        // que no tenga que duplicar la tabla de nombres que ya vive aqui.
        return new ScoreSeriesPointResource(
                point.at(),
                point.score(),
                point.bandLow(),
                point.bandHigh(),
                point.level().name(),
                point.level().getDisplayName());
    }
}
