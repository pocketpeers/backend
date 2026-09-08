package com.pocketpeers.backend.pbl.infrastructure.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuracion de PeerScore.
 *
 * <p>Los valores por defecto estan en las anotaciones y no solo en
 * {@code application.properties}, para que el motor arranque igual en un
 * entorno que no los declare.</p>
 */
@Getter
@Component
public class PeerScoreProperties {

    /**
     * Si el score visible viene de PeerScore o del contador de puntos anterior.
     *
     * <p>Apagarlo no detiene el calculo: PeerScore se sigue guardando en sus
     * propias columnas. Lo unico que cambia es de donde salen el score y el
     * nivel que ve el usuario, y eso permite volver atras sin perder nada.</p>
     */
    private final boolean enabled;

    /** Version del algoritmo con la que se sella cada recalculo, para poder comparar corridas. */
    private final String algoVersion;

    /**
     * Tasa de cumplimiento que se asume cuando el sistema no tiene historial.
     *
     * <p>0.5 es ignorancia maxima: sin datos, el sistema no afirma que la gente
     * cumpla ni que incumpla. Un valor optimista regalaria reputacion a quien
     * todavia no hizo nada, y uno pesimista castigaria por existir.</p>
     */
    private final double neutralOnTimeRate;

    /**
     * Cuantos segundos vive la foto de estadisticas de grupo.
     *
     * <p>Las medianas y tasas por grupo se mueven despacio y recalcularlas exige
     * recorrer toda la evidencia del sistema, asi que se cachean. El costo de
     * que esten algo desactualizadas es despreciable: afectan al prior, no a los
     * hechos del usuario.</p>
     */
    private final long statsCacheSeconds;

    public PeerScoreProperties(
            @Value("${peerscore.enabled:true}") boolean enabled,
            @Value("${peerscore.algo-version:v1}") String algoVersion,
            @Value("${peerscore.neutral-on-time-rate:0.5}") double neutralOnTimeRate,
            @Value("${peerscore.stats-cache-seconds:600}") long statsCacheSeconds) {
        this.enabled = enabled;
        this.algoVersion = algoVersion;
        this.neutralOnTimeRate = neutralOnTimeRate;
        this.statsCacheSeconds = statsCacheSeconds;
    }
}
