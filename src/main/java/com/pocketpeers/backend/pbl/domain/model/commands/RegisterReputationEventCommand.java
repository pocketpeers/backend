package com.pocketpeers.backend.pbl.domain.model.commands;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de un evento de reputacion.
 *
 * <p>Ademas del tipo, lleva los hechos de la obligacion que PeerScore necesita:
 * con quien se contrajo, por cuanto, para cuando y cuando quedo resuelta. Sin
 * ellos el evento solo sirve para el contador de puntos anterior.</p>
 *
 * @param counterpartyId el acreedor de la obligacion, o null si el evento no describe una
 * @param amount         monto de la obligacion, o null en el mismo caso
 * @param dueAt          plazo pactado, o null en el mismo caso
 * @param resolvedAt     cuando quedo resuelta; lo decide quien registra el evento, no el reloj
 */
public record RegisterReputationEventCommand(
        Long userId,
        Long groupId,
        Long paymentId,
        ReputationEventType type,
        String description,
        Long counterpartyId,
        BigDecimal amount,
        LocalDateTime dueAt,
        LocalDateTime resolvedAt
) {

    /**
     * Evento que solo desbloquea insignias.
     *
     * <p>Existe para que los cuatro nulos no se repitan en cada sitio que
     * registra un logro. Son los tipos para los que
     * {@code PaymentOutcome.fromEventType} devuelve null: no producen evidencia
     * de reputacion porque no son el desenlace de una obligacion.</p>
     */
    public static RegisterReputationEventCommand badgeOnly(Long userId, Long groupId, Long paymentId,
                                                           ReputationEventType type, String description) {
        return new RegisterReputationEventCommand(userId, groupId, paymentId, type, description,
                null, null, null, null);
    }
}
