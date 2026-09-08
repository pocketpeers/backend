package com.pocketpeers.backend.pbl.application.internal.queryservices;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.OutcomeRecord;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.PaymentOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * Traduce eventos almacenados a las entradas del calculador.
 *
 * <p>Es el unico lugar donde se decide que cuenta como evidencia de reputacion.
 * Tenerlo centralizado importa porque el score de un usuario, las estadisticas
 * de su grupo y la evidencia inversa de sus contrapartes tienen que estar de
 * acuerdo sobre que eventos existen: si el score contara un evento que las
 * estadisticas no, el prior y el posterior mirarian historiales distintos.</p>
 */
public final class OutcomeRecordAssembler {

    private OutcomeRecordAssembler() {
    }

    public static List<OutcomeRecord> toOutcomes(List<ReputationEvent> events) {
        List<OutcomeRecord> outcomes = new ArrayList<>(events.size());
        for (ReputationEvent event : events) {
            OutcomeRecord outcome = toOutcome(event);
            if (outcome != null) {
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    /**
     * Proyecta un evento, o devuelve null si no es evidencia utilizable.
     *
     * @return null cuando el evento no describe el desenlace de una obligacion,
     *         cuando le faltan los hechos que el calculador necesita, o cuando
     *         el usuario es su propia contraparte
     */
    public static OutcomeRecord toOutcome(ReputationEvent event) {
        PaymentOutcome outcome = PaymentOutcome.fromEventType(event.getType());
        if (outcome == null) {
            // Insignias de tiempo, creacion de grupo, cierre sin deuda y ajustes
            // manuales: no son el desenlace de una obligacion.
            return null;
        }
        if (event.getCounterpartyId() == null || event.getAmount() == null
                || event.getAmount().signum() <= 0) {
            // Eventos anteriores a que estos campos existieran. Se ignoran en vez
            // de inventarles un monto: un monto supuesto entraria en el peso por
            // exposicion y contaminaria el score con un dato que nadie registro.
            return null;
        }
        if (event.getUser() != null && event.getCounterpartyId().equals(event.getUser().getId())) {
            // Alguien que se paga a si mismo. Ocurre de forma legitima cuando el
            // creador de un gasto tiene su propia cuota, y de forma deliberada
            // cuando se busca inflar el score con un grupo de un solo miembro.
            // En los dos casos no hay una segunda persona que atestigue nada.
            return null;
        }

        // Los eventos historicos no tienen `resolvedAt`. `occurredAt` es cuando
        // se registro el evento, que para ellos es la mejor referencia
        // disponible de su antiguedad y permite que cuenten en lugar de perderse.
        var resolvedAt = event.getResolvedAt() != null ? event.getResolvedAt() : event.getOccurredAt();

        return new OutcomeRecord(
                event.getCounterpartyId(),
                event.getGroupId(),
                event.getAmount(),
                outcome,
                resolvedAt);
    }
}
