package com.pocketpeers.backend.pbl.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStats;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStatsSnapshot;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.services.GroupStatsProvider;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifica la conexion entre el historial almacenado y el calculador.
 *
 * <p>Usa el calculador real y no un doble: lo que hay que demostrar aqui es que
 * las propiedades ya probadas del algoritmo sobreviven al pasar por los datos.
 * Un doble probaria que el servicio llama a alguien, que es justo lo que no
 * estaba en duda.</p>
 */
@ExtendWith(MockitoExtension.class)
class PeerScoreServiceImplTests {

    private static final long PAYER = 11L;
    private static final long GROUP = 1L;

    /** Grupo de referencia: mediana S/50 y 70% de cumplimiento historico. */
    private static final GroupStats STATS = new GroupStats(BigDecimal.valueOf(50), 0.70, 1000);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserReputationRepository userReputationRepository;

    @Mock
    private ReputationEventRepository reputationEventRepository;

    @Mock
    private GroupStatsProvider groupStatsProvider;

    private PeerScoreServiceImpl service;

    private PeerScoreServiceImpl service() {
        if (service == null) {
            // Cache desactivada (el ultimo 0) para que las pruebas existentes
            // midan el calculo y no lo que quedo guardado de la llamada previa.
            service = new PeerScoreServiceImpl(userRepository, userReputationRepository,
                    reputationEventRepository, groupStatsProvider,
                    new PeerScoreProperties(true, "v1", 0.5, 600, 0));
        }
        return service;
    }

    /** Servicio con la cache de lectura activa, para las pruebas que la ejercitan. */
    private PeerScoreServiceImpl serviceWithCache(long seconds) {
        return new PeerScoreServiceImpl(userRepository, userReputationRepository,
                reputationEventRepository, groupStatsProvider,
                new PeerScoreProperties(true, "v1", 0.5, 600, seconds));
    }

    @Test
    @DisplayName("Con cache activa, dos lecturas seguidas recalculan una sola vez")
    void laCacheEvitaElSegundoRecalculo() {
        givenReputationFor(PAYER);
        givenStats();
        givenHistory(punctualHistory(10, 3));
        givenNoReverseEvidence();

        var cached = serviceWithCache(60);
        var first = cached.recalculate(PAYER);
        var second = cached.recalculate(PAYER);

        assertThat(second.score()).isEqualTo(first.score());
        // El historial se lee una vez: la segunda lectura no toca la base.
        verify(reputationEventRepository, times(1)).findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(PAYER);
    }

    @Test
    @DisplayName("Un evento invalida la cache: el pago se refleja sin esperar a que expire")
    void unEventoInvalidaLaCache() {
        givenReputationFor(PAYER);
        givenStats();
        givenHistory(punctualHistory(10, 3));
        givenNoReverseEvidence();

        var cached = serviceWithCache(60);
        cached.recalculate(PAYER);
        cached.recalculateAfterEvent(PAYER, null);
        cached.recalculate(PAYER);

        // Tres lecturas del historial: la inicial, la del evento y la de
        // despues. Sin invalidacion, la ultima habria devuelto lo cacheado.
        verify(reputationEventRepository, atLeast(3)).findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(PAYER);
    }

    @Test
    @DisplayName("Con cache en cero se recalcula siempre, como antes")
    void cacheDesactivadaRecalculaSiempre() {
        givenReputationFor(PAYER);
        givenStats();
        givenHistory(punctualHistory(10, 3));
        givenNoReverseEvidence();

        var sinCache = serviceWithCache(0);
        sinCache.recalculate(PAYER);
        sinCache.recalculate(PAYER);

        verify(reputationEventRepository, times(2)).findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(PAYER);
    }

    @Test
    void recalculatePersistsTheScoreWithItsBandAndAudit() {
        var reputation = givenReputationFor(PAYER);
        givenStats();
        givenHistory(punctualHistory(40, 1));
        givenNoReverseEvidence();

        var result = service().recalculate(PAYER);

        verify(userReputationRepository).save(reputation);
        assertThat(reputation.getPeerScore()).isEqualTo(result.score()).isGreaterThan(90.0);
        assertThat(reputation.getBandLow()).isLessThan(reputation.getBandHigh());
        assertThat(reputation.getAlpha()).isPositive();
        assertThat(reputation.getBeta()).isPositive();
        assertThat(reputation.getAlgoVersion()).isEqualTo("v1");
        assertThat(reputation.getComputedAt()).isNotNull();
        assertThat(reputation.hasPeerScore()).isTrue();

        // El puntaje sube porque los pagos existen, pero una sola contraparte no
        // alcanza la diversidad minima: es la defensa contra colusion llegando
        // intacta hasta la fila guardada.
        assertThat(reputation.getPeerLevel()).isEqualTo(ReputationLevel.NEW);
        assertThat(reputation.getDistinctCounterparties()).isEqualTo(1);

        // El contador anterior queda intacto: los dos motores conviven, y por eso
        // apagar PeerScore no pierde nada.
        assertThat(reputation.getScore()).isZero();
    }

    @Test
    void reciprocalEvidenceLowersTheScoreOfTheSameHistory() {
        // Es la prueba de que la evidencia inversa esta realmente conectada. Si
        // el servicio pasara un mapa vacio, el algoritmo seguiria siendo correcto
        // y ninguna otra prueba lo notaria: los dos pares darian el mismo score y
        // la defensa contra pares reciprocos no existiria en la practica.
        var reputation = givenReputationFor(PAYER);
        givenStats();
        var forward = punctualHistory(12, 3);
        givenHistory(forward);

        // Primera corrida sin evidencia inversa, segunda con la contraparte
        // dominante devolviendo exactamente lo mismo.
        when(reputationEventRepository.findAllByCounterpartyIdAndAmountIsNotNull(PAYER))
                .thenReturn(List.of(), reverseOf(forward, 10L));

        service().recalculate(PAYER);
        double withoutReciprocity = reputation.getPeerScore();

        service().recalculate(PAYER);
        double withReciprocity = reputation.getPeerScore();

        assertThat(withReciprocity).isLessThan(withoutReciprocity);
    }

    @Test
    void paymentsToOneselfLeaveTheUserWithNoEvidence() {
        // Un grupo de un solo miembro registrando y pagando sus propios gastos.
        // El resultado tiene que ser el de alguien sin historial: el prior del
        // sistema, no un score alto.
        var reputation = givenReputationFor(PAYER);
        givenStats();
        givenHistory(List.of(
                outcome(PAYER, PAYER, 50, ReputationEventType.ON_TIME_PAYMENT, 1),
                outcome(PAYER, PAYER, 50, ReputationEventType.ON_TIME_PAYMENT, 2)));
        givenNoReverseEvidence();

        var result = service().recalculate(PAYER);

        assertThat(result.score()).isEqualTo(70.0);
        assertThat(result.distinctCounterparties()).isZero();
        assertThat(reputation.getPeerLevel()).isEqualTo(ReputationLevel.NEW);
    }

    @Test
    void recalculateAfterEventAlsoRecalculatesTheCounterparty() {
        // La reciprocidad entre los dos cambio, asi que el descuento que se le
        // aplica a la contraparte respecto de este usuario tambien.
        var payerReputation = givenReputationFor(PAYER);
        var counterpartyReputation = givenReputationFor(7L);
        givenStats();
        when(reputationEventRepository.findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(any()))
                .thenReturn(List.of());
        when(reputationEventRepository.findAllByCounterpartyIdAndAmountIsNotNull(any()))
                .thenReturn(List.of());

        service().recalculateAfterEvent(PAYER, 7L);

        verify(userReputationRepository).save(payerReputation);
        verify(userReputationRepository).save(counterpartyReputation);
    }

    @Test
    void recalculateAfterEventIgnoresAMissingCounterparty() {
        // Corre dentro de la confirmacion de un pago: no vale hacer fallar un
        // pago real porque no se pudo actualizar la reputacion de un tercero.
        var reputation = givenReputationFor(PAYER);
        givenStats();
        givenHistory(List.of());
        givenNoReverseEvidence();
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        service().recalculateAfterEvent(PAYER, 999L);

        verify(userReputationRepository).save(reputation);
    }

    @Test
    void recalculateAllReportsWhoChangedLevel() {
        // El nocturno existe porque el score se mueve sin eventos, y quien cruza
        // un nivel asi tambien tiene que recibir su insignia.
        var reputation = givenReputationFor(PAYER);
        ReflectionTestUtils.setField(reputation, "peerLevel", ReputationLevel.GOLD);
        when(userReputationRepository.findAll()).thenReturn(List.of(reputation));
        givenStats();
        givenHistory(List.of());
        givenNoReverseEvidence();

        var changed = service().recalculateAll();

        assertThat(reputation.getPeerLevel()).isEqualTo(ReputationLevel.NEW);
        assertThat(changed).containsExactly(PAYER);
    }

    @Test
    void recalculateFailsForAnUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        when(groupStatsProvider.snapshot()).thenReturn(new GroupStatsSnapshot(Map.of(), STATS));

        assertThatThrownBy(() -> service().recalculate(404L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /**
     * Solo el usuario, sin su reputacion guardada.
     *
     * <p>La serie no lee ni escribe el agregado: es una consulta historica. Darle
     * la reputacion de todas formas dejaria un stub que nadie usa, y Mockito
     * estricto lo rechaza con razon.</p>
     */
    private void givenUser(long userId) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
    }

    private UserReputation givenReputationFor(long userId) {
        var user = user(userId);
        var reputation = new UserReputation(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userReputationRepository.findByUser_Id(userId)).thenReturn(Optional.of(reputation));
        return reputation;
    }

    private void givenStats() {
        when(groupStatsProvider.snapshot()).thenReturn(new GroupStatsSnapshot(Map.of(GROUP, STATS), STATS));
    }

    private void givenHistory(List<ReputationEvent> events) {
        when(reputationEventRepository.findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(PAYER))
                .thenReturn(events);
    }

    private void givenNoReverseEvidence() {
        when(reputationEventRepository.findAllByCounterpartyIdAndAmountIsNotNull(PAYER))
                .thenReturn(List.of());
    }

    private List<ReputationEvent> punctualHistory(int count, int counterparties) {
        List<ReputationEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(outcome(PAYER, 10L + (i % counterparties), 50,
                    ReputationEventType.ON_TIME_PAYMENT, i));
        }
        return events;
    }

    /** Los mismos hechos vistos al reves: lo que esa contraparte le debia al usuario. */
    private List<ReputationEvent> reverseOf(List<ReputationEvent> forward, long counterparty) {
        return forward.stream()
                .filter(event -> event.getCounterpartyId().equals(counterparty))
                .map(event -> outcome(counterparty, PAYER, event.getAmount().doubleValue(),
                        event.getType(), 0))
                .toList();
    }

    private ReputationEvent outcome(long payerId, long counterpartyId, double amount,
                                    ReputationEventType type, long daysAgo) {
        var resolvedAt = LocalDateTime.now().minusDays(daysAgo);
        return new ReputationEvent(user(payerId), GROUP, 42L, type, 3, 3, "evento",
                counterpartyId, BigDecimal.valueOf(amount), resolvedAt, resolvedAt);
    }

    private User user(long id) {
        var user = new User("user" + id, "secret");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
