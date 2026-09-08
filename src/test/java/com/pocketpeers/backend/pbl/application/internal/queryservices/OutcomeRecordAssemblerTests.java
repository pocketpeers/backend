package com.pocketpeers.backend.pbl.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.PaymentOutcome;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Que cuenta como evidencia de reputacion y que no.
 *
 * <p>Importa que este filtro sea uno solo: el score de un usuario, el prior de
 * su grupo y la evidencia inversa de sus contrapartes se calculan sobre el mismo
 * historial, y si no coincidieran en que eventos existen mirarian datos
 * distintos.</p>
 */
class OutcomeRecordAssemblerTests {

    private static final LocalDateTime RESOLVED = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Test
    void projectsAConfirmedPaymentIntoEvidence() {
        var event = event(11L, ReputationEventType.ON_TIME_PAYMENT, 7L, new BigDecimal("80.00"), RESOLVED);

        var outcome = OutcomeRecordAssembler.toOutcome(event);

        assertThat(outcome).isNotNull();
        assertThat(outcome.counterpartyId()).isEqualTo(7L);
        assertThat(outcome.amount()).isEqualByComparingTo("80.00");
        assertThat(outcome.outcome()).isEqualTo(PaymentOutcome.PUNTUAL);
        assertThat(outcome.resolvedAt()).isEqualTo(RESOLVED);
    }

    @Test
    void ignoresEventsThatOnlyUnlockBadges() {
        // Crear un grupo o cerrar el mes sin deuda son logros, no obligaciones
        // cumplidas: no hay contraparte que atestigue nada.
        var event = event(11L, ReputationEventType.GROUP_CREATED, null, null, null);

        assertThat(OutcomeRecordAssembler.toOutcome(event)).isNull();
    }

    @Test
    void ignoresEventsFromBeforeTheFactsWereRecorded() {
        // Se descartan en vez de inventarles un monto: un monto supuesto entraria
        // en el peso por exposicion y contaminaria el score con un dato que nadie
        // registro nunca.
        var event = event(11L, ReputationEventType.ON_TIME_PAYMENT, null, null, null);

        assertThat(OutcomeRecordAssembler.toOutcome(event)).isNull();
    }

    @Test
    void ignoresPaymentsWhereTheUserIsTheirOwnCounterparty() {
        // Es la forma mas simple de inflarse: un grupo de un solo miembro que se
        // registra gastos y los paga puntual. Sin este filtro seria evidencia.
        var event = event(11L, ReputationEventType.ON_TIME_PAYMENT, 11L, new BigDecimal("50.00"), RESOLVED);

        assertThat(OutcomeRecordAssembler.toOutcome(event)).isNull();
    }

    @Test
    void fallsBackToTheRegistrationInstantWhenTheResolutionIsMissing() {
        // Filas historicas: tienen contraparte y monto por un backfill, pero no
        // el instante de resolucion. Usar `occurredAt` las deja participar en vez
        // de perderlas.
        var event = event(11L, ReputationEventType.LATE_PAYMENT, 7L, new BigDecimal("30.00"), null);

        var outcome = OutcomeRecordAssembler.toOutcome(event);

        assertThat(outcome).isNotNull();
        assertThat(outcome.resolvedAt()).isEqualTo(event.getOccurredAt());
    }

    @Test
    void projectsOnlyTheUsableEventsOfAList() {
        var usable = event(11L, ReputationEventType.ON_TIME_PAYMENT, 7L, new BigDecimal("80.00"), RESOLVED);
        var badgeOnly = event(11L, ReputationEventType.EARLY_PAYMENT, null, null, null);
        var selfPaid = event(11L, ReputationEventType.ON_TIME_PAYMENT, 11L, new BigDecimal("10.00"), RESOLVED);

        assertThat(OutcomeRecordAssembler.toOutcomes(List.of(usable, badgeOnly, selfPaid)))
                .hasSize(1);
    }

    private ReputationEvent event(Long payerId, ReputationEventType type, Long counterpartyId,
                                  BigDecimal amount, LocalDateTime resolvedAt) {
        var payer = new User("ana", "secret");
        ReflectionTestUtils.setField(payer, "id", payerId);
        return new ReputationEvent(payer, 1L, 42L, type, 3, 3, "evento",
                counterpartyId, amount, RESOLVED, resolvedAt);
    }
}
