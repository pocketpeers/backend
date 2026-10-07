package com.pocketpeers.backend.pbl.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * Estadisticas de referencia de un grupo.
 *
 * <p>El score solo usa la mediana, como escala de los montos. La tasa de
 * cumplimiento ya no es el punto de partida de nadie (ver
 * {@code PeerScoreCalculator}): se conserva como dato descriptivo del grupo.</p>
 *
 * <p>Los montos se normalizan contra la mediana del grupo y no contra un valor
 * absoluto en soles. Esa decision es deliberada: un grupo que mueve S/20 y otro
 * que mueve S/500 deben poder alcanzar el mismo score con la misma conducta. Un
 * score que castigara sistematicamente los montos pequenos seria lo contrario de
 * la inclusion financiera que persigue el producto.</p>
 *
 * @param medianAmount mediana de montos del grupo
 * @param onTimeRate   proporcion historica de cumplimiento, en [0, 1]
 * @param eventCount   cuantos eventos respaldan estas estadisticas
 */
public record GroupStats(
        BigDecimal medianAmount,
        double onTimeRate,
        int eventCount
) {

    public GroupStats {
        if (medianAmount == null || medianAmount.signum() <= 0) {
            throw new IllegalArgumentException("medianAmount debe ser positivo");
        }
        if (onTimeRate < 0 || onTimeRate > 1) {
            throw new IllegalArgumentException("onTimeRate debe estar en [0, 1]");
        }
    }

    /** Si el grupo tiene pocos eventos, sus estadisticas no son representativas. */
    public boolean isReliable() {
        return eventCount >= ScoreParameters.MIN_GROUP_EVENTS;
    }
}
