package com.pocketpeers.backend.pbl.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.services.PeerScoreService;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.BadgeCatalogRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserBadgeRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PblCommandServiceImplTests {

    private static final long USER = 11L;
    private static final long COUNTERPARTY = 7L;
    private static final LocalDateTime RESOLVED = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserReputationRepository userReputationRepository;

    @Mock
    private ReputationEventRepository reputationEventRepository;

    @Mock
    private BadgeCatalogRepository badgeCatalogRepository;

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @Mock
    private PeerScoreService peerScoreService;

    @Test
    void eventStoresTheObligationFactsAndTriggersARecalculation() {
        // El evento es la unica entrada de PeerScore. Si no guardara la
        // contraparte, el monto y las fechas, el motor nuevo se quedaria sin los
        // hechos y el recalculo no tendria nada que leer.
        var reputation = givenReputationFor(USER);
        when(reputationEventRepository.save(any(ReputationEvent.class))).thenAnswer(invocation -> {
            ReputationEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 5L);
            return event;
        });

        var eventId = service(true).handle(new RegisterReputationEventCommand(
                USER, 1L, 42L, ReputationEventType.ON_TIME_PAYMENT, "pago puntual",
                COUNTERPARTY, new BigDecimal("80.00"), RESOLVED.plusDays(1), RESOLVED));

        assertThat(eventId).isEqualTo(5L);

        ArgumentCaptor<ReputationEvent> captor = ArgumentCaptor.forClass(ReputationEvent.class);
        verify(reputationEventRepository).save(captor.capture());
        var stored = captor.getValue();
        assertThat(stored.getCounterpartyId()).isEqualTo(COUNTERPARTY);
        assertThat(stored.getAmount()).isEqualByComparingTo("80.00");
        assertThat(stored.getDueAt()).isEqualTo(RESOLVED.plusDays(1));
        assertThat(stored.getResolvedAt()).isEqualTo(RESOLVED);

        // Y el recalculo alcanza a la contraparte, cuya reciprocidad cambio.
        verify(peerScoreService).recalculateAfterEvent(USER, COUNTERPARTY);

        // El contador anterior sigue sumando: los dos motores se mantienen al dia
        // para que el interruptor de configuracion sea reversible.
        assertThat(reputation.getScore()).isEqualTo(3);
    }

    @Test
    void levelBadgesFollowThePeerLevelWhenPeerScoreIsOn() {
        // Score 90 con una sola contraparte: el contador anterior daria Plata,
        // PeerScore deja a la persona en Nuevo porque no hay con que distinguirla
        // de dos cuentas que se pagan entre si. Dar la insignia contradiria justo
        // la defensa que hace inutil la colusion.
        var reputation = givenReputationFor(USER);
        ReflectionTestUtils.setField(reputation, "score", 90);
        givenPeerLevel(reputation, ReputationLevel.NEW, 90.0);
        givenSavedEvent();

        service(true).handle(badgeOnlyEvent());

        verify(userBadgeRepository, never()).existsByUser_IdAndBadge_Code(any(), eq("SILVER_LEVEL"));
        verify(userBadgeRepository, never()).existsByUser_IdAndBadge_Code(any(), eq("GOLD_LEVEL"));
    }

    @Test
    void levelBadgesFallBackToThePointCounterWhenPeerScoreIsOff() {
        var reputation = givenReputationFor(USER);
        ReflectionTestUtils.setField(reputation, "score", 90);
        givenPeerLevel(reputation, ReputationLevel.NEW, 90.0);
        givenSavedEvent();

        service(false).handle(badgeOnlyEvent());

        verify(userBadgeRepository).existsByUser_IdAndBadge_Code(USER, "SILVER_LEVEL");
        verify(userBadgeRepository).existsByUser_IdAndBadge_Code(USER, "GOLD_LEVEL");
    }

    @Test
    void levelBadgesAlsoFollowThePointCounterWhileNobodyHasBeenRecalculated() {
        // Filas anteriores al motor nuevo: sin recalculo encima no hay nivel de
        // PeerScore que consultar, y negarles la insignia les quitaria una que ya
        // tenian ganada con las reglas vigentes cuando la ganaron.
        var reputation = givenReputationFor(USER);
        ReflectionTestUtils.setField(reputation, "score", 90);
        givenSavedEvent();

        service(true).handle(badgeOnlyEvent());

        verify(userBadgeRepository).existsByUser_IdAndBadge_Code(USER, "SILVER_LEVEL");
    }

    @Test
    void refreshLevelBadgesReevaluatesTheLevelOnesAndNothingElse() {
        // Lo llama el nocturno: el score decae solo, asi que alguien puede cruzar
        // un nivel sin haber registrado ningun evento. Los demas logros si
        // dependen de eventos y no tienen por que revisarse.
        var reputation = new UserReputation(user(USER));
        givenPeerLevel(reputation, ReputationLevel.GOLD, 90.0);
        when(userReputationRepository.findByUser_Id(USER)).thenReturn(Optional.of(reputation));

        service(true).refreshLevelBadges(USER);

        verify(userBadgeRepository).existsByUser_IdAndBadge_Code(USER, "SILVER_LEVEL");
        verify(userBadgeRepository).existsByUser_IdAndBadge_Code(USER, "GOLD_LEVEL");
        verify(userBadgeRepository, never()).existsByUser_IdAndBadge_Code(any(), eq("FIRST_PAYMENT"));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private PblCommandServiceImpl service(boolean peerScoreEnabled) {
        return new PblCommandServiceImpl(userRepository, userReputationRepository, reputationEventRepository,
                badgeCatalogRepository, userBadgeRepository, peerScoreService,
                new PeerScoreProperties(peerScoreEnabled, "v1", 0.5, 600));
    }

    private RegisterReputationEventCommand badgeOnlyEvent() {
        return RegisterReputationEventCommand.badgeOnly(USER, 1L, null,
                ReputationEventType.GROUP_CREATED, "grupo creado");
    }

    private UserReputation givenReputationFor(long userId) {
        var user = user(userId);
        var reputation = new UserReputation(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userReputationRepository.findByUser_Id(userId)).thenReturn(Optional.of(reputation));
        return reputation;
    }

    private void givenPeerLevel(UserReputation reputation, ReputationLevel level, double score) {
        ReflectionTestUtils.setField(reputation, "peerScore", score);
        ReflectionTestUtils.setField(reputation, "peerLevel", level);
        ReflectionTestUtils.setField(reputation, "bandLow", score - 5);
        ReflectionTestUtils.setField(reputation, "bandHigh", score + 5);
        ReflectionTestUtils.setField(reputation, "effectiveCounterparties", 1.0);
    }

    private void givenSavedEvent() {
        when(reputationEventRepository.save(any(ReputationEvent.class))).thenAnswer(invocation -> {
            ReputationEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 5L);
            return event;
        });
    }

    private User user(long id) {
        var user = new User("user" + id, "secret");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
