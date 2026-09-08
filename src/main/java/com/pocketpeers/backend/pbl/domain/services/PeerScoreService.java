package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.NextLevelGoal;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;

import java.util.List;
import java.util.Map;

/**
 * Puente entre el calculador puro y los datos del sistema.
 *
 * <p>El calculador no consulta nada: recibe el historial ya cargado. Este
 * servicio es quien lo carga, lo proyecta, arma la evidencia inversa que exige
 * el indice de reciprocidad y guarda el resultado.</p>
 */
public interface PeerScoreService {

    /** Recalcula el score de un usuario desde su historial completo y lo persiste. */
    ScoreResult recalculate(Long userId);

    /**
     * Recalcula al usuario y a la contraparte del evento que acaba de ocurrir.
     *
     * <p>La contraparte tambien cambia: la reciprocidad entre los dos se mide
     * comparando la evidencia en ambos sentidos, asi que un pago nuevo en una
     * direccion altera el descuento que se aplica en la otra.</p>
     */
    void recalculateAfterEvent(Long userId, Long counterpartyId);

    /**
     * Recalcula a todos los usuarios con una sola foto de estadisticas.
     *
     * @return los ids cuyo nivel cambio en esta corrida
     */
    List<Long> recalculateAll();

    /**
     * Recalcula a un conjunto de usuarios con una sola foto y un solo instante.
     *
     * <p>Existe para cuando los resultados se van a comparar entre si, que es el
     * caso del ranking de un grupo. Pedir el score de cada miembro por separado
     * los mide con reglas distintas: entre una llamada y otra puede expirar la
     * cache de estadisticas, y el decaimiento avanza con el reloj. Compartir la
     * foto y el instante es la misma razon por la que
     * {@link #recalculateAfterEvent} recalcula a los dos lados a la vez.</p>
     *
     * @return el resultado de cada usuario que existe, indexado por id
     */
    Map<Long, ScoreResult> recalculateFor(List<Long> userIds);

    /** Que le falta para el siguiente nivel, o null si ya esta en el maximo. */
    NextLevelGoal goalFor(ScoreResult result);
}
