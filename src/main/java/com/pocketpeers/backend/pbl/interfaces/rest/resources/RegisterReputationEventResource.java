package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro manual de un evento de reputacion.
 *
 * <p>Los cuatro campos de la obligacion son opcionales. Un evento sin ellos
 * sigue sirviendo para el contador de puntos y para las insignias, pero no
 * produce evidencia para PeerScore: sin contraparte ni monto no hay nada que
 * pesar ni con quien contrastarlo.</p>
 */
public record RegisterReputationEventResource(
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
}
