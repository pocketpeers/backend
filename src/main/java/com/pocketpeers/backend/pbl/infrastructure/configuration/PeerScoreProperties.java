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

    /**
     * Cuantos segundos vale un score ya calculado antes de recalcularlo.
     *
     * <p>El score se recalcula al leerlo, no se sirve el ultimo guardado, y hay
     * dos razones para ello: el desglose explicativo no se persiste y el score
     * decae de forma continua. Pero recalcular desde el historial completo en
     * cada peticion se nota cuando hay cientos de usuarios y el panel lo pide en
     * cada carga.</p>
     *
     * <p>Una ventana corta concilia las dos cosas. Con vida media de 90 dias, un
     * valor de hace un minuto es indistinguible del actual; lo que no se puede
     * es servir el de hace horas. La cache ademas se invalida al registrar un
     * evento, asi que un pago se refleja en el acto y no al expirar.</p>
     *
     * <p>Cero la desactiva y vuelve al comportamiento anterior.</p>
     */
    private final long scoreCacheSeconds;

    public PeerScoreProperties(
            @Value("${peerscore.enabled:true}") boolean enabled,
            @Value("${peerscore.algo-version:v1}") String algoVersion,
            @Value("${peerscore.neutral-on-time-rate:0.5}") double neutralOnTimeRate,
            @Value("${peerscore.stats-cache-seconds:600}") long statsCacheSeconds,
            @Value("${peerscore.score-cache-seconds:60}") long scoreCacheSeconds) {
        this.enabled = enabled;
        this.algoVersion = algoVersion;
        this.neutralOnTimeRate = neutralOnTimeRate;
        this.statsCacheSeconds = statsCacheSeconds;
        this.scoreCacheSeconds = scoreCacheSeconds;
    }
}
