package com.pocketpeers.backend.pbl.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupStatsProviderImplTests {

    @Mock
    private ReputationEventRepository reputationEventRepository;

    @Test
    void medianIgnoresTheOutlierThatWouldMoveAnAverage() {
        // La mediana define la escala del grupo. Con promedio, un solo gasto de
        // S/1000 volveria "pequeno" todo lo demas y hundiria el peso de los
        // pagos normales del grupo.
        var provider = providerOver(List.of(
                outcome(10.0, ReputationEventType.ON_TIME_PAYMENT),
                outcome(20.0, ReputationEventType.ON_TIME_PAYMENT),
                outcome(1000.0, ReputationEventType.ON_TIME_PAYMENT)));

        assertThat(provider.snapshot().byGroup().get(1L).medianAmount())
                .isEqualByComparingTo("20.0");
    }

    @Test
    void medianOfAnEvenNumberOfAmountsAveragesTheMiddleTwo() {
        var provider = providerOver(List.of(
                outcome(10.0, ReputationEventType.ON_TIME_PAYMENT),
                outcome(30.0, ReputationEventType.ON_TIME_PAYMENT)));

        assertThat(provider.snapshot().byGroup().get(1L).medianAmount())
                .isEqualByComparingTo("20.0000");
    }

    @Test
    void complianceRateAveragesTheOutcomeValues() {
        // Puntual vale 1.0 y vencido 0.0, asi que mitad y mitad da 0.5. Un grupo
        // de puros abonos parciales a tiempo daria 0.6, no 0 ni 1.
        var provider = providerOver(outcomes(ReputationEventType.ON_TIME_PAYMENT, 10,
                ReputationEventType.OVERDUE_PAYMENT, 10));

        var snapshot = provider.snapshot();
        assertThat(snapshot.byGroup().get(1L).onTimeRate()).isEqualTo(0.5);
        assertThat(snapshot.global().onTimeRate()).isEqualTo(0.5);
    }

    @Test
    void aYoungSystemUsesTheNeutralRateAsPriorButItsRealMedian() {
        // Regresion encontrada corriendo la aplicacion. Con los primeros pagos
        // del sistema todos puntuales, la tasa observada es 1.0 y el prior queda
        // en alpha = 4, beta = 0: cualquier usuario sin un solo pago recibia
        // score 100, y la banda de quien tenia tres pagos se cerraba a 0.05
        // puntos de ancho cuando su unica funcion es decir que el sistema no
        // sabe lo suficiente.
        var provider = providerOver(outcomes(ReputationEventType.ON_TIME_PAYMENT, 3));

        var global = provider.snapshot().global();

        assertThat(global.onTimeRate()).isEqualTo(0.5);
        assertThat(global.medianAmount()).isEqualByComparingTo("50.0");
        assertThat(global.isReliable()).isFalse();
    }

    @Test
    void aSystemWithEnoughHistoryUsesItsObservedRate() {
        var provider = providerOver(outcomes(ReputationEventType.ON_TIME_PAYMENT, 20));

        var global = provider.snapshot().global();

        assertThat(global.onTimeRate()).isEqualTo(1.0);
        assertThat(global.isReliable()).isTrue();
    }

    @Test
    void aSmallGroupIsNotConsideredReliable() {
        // Es lo que evita que un grupo con dos pagos puntuales le regale un
        // prior del 100% a todos sus miembros.
        var provider = providerOver(outcomes(ReputationEventType.ON_TIME_PAYMENT, 2));

        assertThat(provider.snapshot().byGroup().get(1L).isReliable()).isFalse();
    }

    @Test
    void anEmptySystemFallsBackToMaximumIgnorance() {
        // Sin historial el sistema no afirma que la gente cumpla ni que
        // incumpla. La mediana solo tiene que ser positiva: es un divisor y no
        // hay ningun evento que pesar todavia.
        var provider = providerOver(List.of());

        var snapshot = provider.snapshot();

        assertThat(snapshot.byGroup()).isEmpty();
        assertThat(snapshot.global().onTimeRate()).isEqualTo(0.5);
        assertThat(snapshot.global().medianAmount()).isPositive();
        assertThat(snapshot.global().eventCount()).isZero();
    }

    @Test
    void theEmptySnapshotIsNeverCached() {
        // Regresion encontrada corriendo la aplicacion. La mediana del sistema
        // vacio es un relleno de 1; si esa foto se guardara, durante los diez
        // minutos siguientes cada pago real se pesaria contra una mediana de S/1
        // y saturaria el techo de peso, dando scores altisimos con tres pagos.
        var provider = providerOver(List.of());

        provider.snapshot();
        provider.snapshot();

        verify(reputationEventRepository, times(2))
                .findAllByCounterpartyIdIsNotNullAndAmountIsNotNull();
    }

    @Test
    void theSnapshotIsCachedAndInvalidateForcesARecount() {
        var provider = providerOver(List.of(outcome(50.0, ReputationEventType.ON_TIME_PAYMENT)));

        provider.snapshot();
        provider.snapshot();
        verify(reputationEventRepository, times(1))
                .findAllByCounterpartyIdIsNotNullAndAmountIsNotNull();

        provider.invalidate();
        provider.snapshot();
        verify(reputationEventRepository, times(2))
                .findAllByCounterpartyIdIsNotNullAndAmountIsNotNull();
    }

    private GroupStatsProviderImpl providerOver(List<ReputationEvent> events) {
        when(reputationEventRepository.findAllByCounterpartyIdIsNotNullAndAmountIsNotNull())
                .thenReturn(events);
        return new GroupStatsProviderImpl(reputationEventRepository,
                new PeerScoreProperties(true, "v1", 0.5, 600));
    }

    /** Lotes de eventos por tipo, para pasar el piso de confiabilidad global. */
    private static List<ReputationEvent> outcomes(Object... typeAndCount) {
        List<ReputationEvent> events = new java.util.ArrayList<>();
        for (int i = 0; i < typeAndCount.length; i += 2) {
            var type = (ReputationEventType) typeAndCount[i];
            var count = (Integer) typeAndCount[i + 1];
            for (int n = 0; n < count; n++) {
                events.add(outcome(50.0, type));
            }
        }
        return events;
    }

    private static ReputationEvent outcome(double amount, ReputationEventType type) {
        var payer = new User("ana", "secret");
        ReflectionTestUtils.setField(payer, "id", 11L);
        return new ReputationEvent(payer, 1L, 42L, type, 3, 3, "evento",
                7L, BigDecimal.valueOf(amount), LocalDateTime.now(), LocalDateTime.now());
    }
}
