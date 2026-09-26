package com.pocketpeers.backend.pbl.interfaces.rest.resources;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;

import java.time.LocalDateTime;

public record ReputationEventResource(
        Long id,
        Long userId,
        Long groupId,
        Long paymentId,
        ReputationEventType type,
        int pointsDelta,
        int resultingScore,
        String description,
        LocalDateTime occurredAt,
        // Si el evento entro en el calculo del score. Sale de la misma regla que
        // usa el motor (OutcomeRecordAssembler), para que la app no tenga que
        // adivinarlo por el tipo: un «pago a tiempo» de la cuota propia tiene el
        // mismo tipo que cualquier otro y aun asi no cuenta.
        boolean countsForScore,
        // Pago de la propia cuota de un gasto que la persona creo.
        boolean ownExpense
) {
}
