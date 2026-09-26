package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class ReputationEventResourceFromEntityAssemblerTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 10, 0);

    private final User ana = user(2L);

    @Test
    void aPaymentToSomeoneElseCountsForTheScore() {
        var resource = ReputationEventResourceFromEntityAssembler.toResourceFromEntity(
                event(ReputationEventType.ON_TIME_PAYMENT, 1L));

        assertThat(resource.countsForScore()).isTrue();
        assertThat(resource.ownExpense()).isFalse();
    }

    @Test
    void payingYourOwnShareDoesNotCount() {
        // El creador del gasto es la contraparte de su propia cuota: el motor lo
        // descarta y la app no debe mostrarlo como si subiera el score.
        var resource = ReputationEventResourceFromEntityAssembler.toResourceFromEntity(
                event(ReputationEventType.ON_TIME_PAYMENT, 2L));

        assertThat(resource.countsForScore()).isFalse();
        assertThat(resource.ownExpense()).isTrue();
    }

    @Test
    void badgeOnlyEventsDoNotCount() {
        var resource = ReputationEventResourceFromEntityAssembler.toResourceFromEntity(
                event(ReputationEventType.RECEIPT_ATTACHED, null));

        assertThat(resource.countsForScore()).isFalse();
        assertThat(resource.ownExpense()).isFalse();
    }

    private ReputationEvent event(ReputationEventType type, Long counterpartyId) {
        return new ReputationEvent(ana, 10L, 5L, type, 0, 0, "", counterpartyId,
                counterpartyId == null ? null : new BigDecimal("69.50"), NOW, NOW);
    }

    private static User user(Long id) {
        var user = new User("mialaos", "x");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
