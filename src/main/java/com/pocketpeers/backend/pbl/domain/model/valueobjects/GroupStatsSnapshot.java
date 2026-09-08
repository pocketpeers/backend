package com.pocketpeers.backend.pbl.domain.model.valueobjects;

import java.util.Map;

/**
 * Estadisticas de todos los grupos y su respaldo global, tomadas en un instante.
 *
 * <p>Van juntas a proposito. El calculador necesita las dos y
 * {@code GroupStats.resolve} elige entre ellas segun si el grupo tiene
 * suficientes eventos, asi que separarlas permitiria calcular un score con la
 * mediana de un grupo y el respaldo de otro momento.</p>
 *
 * <p>Recalcular a todos los usuarios con una sola foto tambien hace el resultado
 * comparable entre ellos: si las estadisticas cambiaran a mitad de una corrida
 * nocturna, dos usuarios con el mismo historial obtendrian scores distintos
 * segun el orden en que les toco.</p>
 */
public record GroupStatsSnapshot(
        Map<Long, GroupStats> byGroup,
        GroupStats global
) {

    public GroupStatsSnapshot {
        if (global == null) {
            throw new IllegalArgumentException("global es obligatorio: es el respaldo de todo grupo pequeno");
        }
        byGroup = byGroup == null ? Map.of() : Map.copyOf(byGroup);
    }
}
