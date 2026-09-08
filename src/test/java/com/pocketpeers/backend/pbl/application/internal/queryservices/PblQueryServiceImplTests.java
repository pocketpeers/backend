package com.pocketpeers.backend.pbl.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.queries.GetGroupLeaderboardQuery;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import com.pocketpeers.backend.pbl.domain.services.PeerScoreService;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.BadgeCatalogRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserBadgeRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifica que el ranking de un grupo compare a sus miembros en la misma escala.
 *
 * <p>Es la unica pantalla donde los scores de personas distintas se ponen uno al
 * lado del otro y se ordenan, asi que es tambien la unica donde mezclar unidades
 * produce una afirmacion falsa en vez de un numero raro.</p>
 */
@ExtendWith(MockitoExtension.class)
class PblQueryServiceImplTests {

    private static final long GROUP = 1L;

    /** Quien ya tenia un PeerScore guardado. */
    private static final long RECALCULATED = 1L;

    /** Quien nunca fue recalculado: su fila solo tiene el contador viejo. */
    private static final long NEVER_RECALCULATED = 2L;

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
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserInformationRepository userInformationRepository;

    @Mock
    private PeerScoreService peerScoreService;

    private PblQueryServiceImpl service(boolean peerScoreEnabled) {
        return new PblQueryServiceImpl(userRepository, userReputationRepository, reputationEventRepository,
                badgeCatalogRepository, userBadgeRepository, groupMemberRepository, userInformationRepository,
                peerScoreService, new PeerScoreProperties(peerScoreEnabled, "v1", 0.5, 600));
    }

    @Test
    void everyMemberIsRankedOnTheSameScale() {
        // El caso que rompia: un miembro con PeerScore guardado y otro sin el.
        // Antes el segundo caia al contador viejo, y la tabla terminaba
        // ordenando 50 puntos de una escala contra 3 de otra. El 3 no era un
        // score bajo: era otra unidad.
        givenMembers(RECALCULATED, NEVER_RECALCULATED);
        givenReputation(RECALCULATED, 0, 50.0);
        givenReputation(NEVER_RECALCULATED, 3, null);
        givenFreshScores(Map.of(
                RECALCULATED, scoreOf(50.0),
                NEVER_RECALCULATED, scoreOf(50.0)));

        var entries = service(true).handle(new GetGroupLeaderboardQuery(GROUP, RECALCULATED));

        assertThat(entries).extracting("score").containsExactly(50, 50);
        assertThat(entries).extracting("peerScore").containsExactly(50.0, 50.0);
        // Mismo score y mismas insignias: el desempate es determinista, pero lo
        // que importa es que ninguno quedo medido con la regla del otro.
        assertThat(entries).extracting("level").containsExactly("Nuevo", "Nuevo");
    }

    @Test
    void theRankingDoesNotDependOnWhoWasRecalculatedBefore() {
        // Consultar el perfil publico de alguien persiste su PeerScore. Si el
        // ranking leyera la fila guardada, su orden cambiaria segun que perfiles
        // hubiera abierto el usuario antes de mirarlo.
        givenMembers(RECALCULATED, NEVER_RECALCULATED);
        givenReputation(RECALCULATED, 90, 20.0);
        givenReputation(NEVER_RECALCULATED, 0, null);
        givenFreshScores(Map.of(
                RECALCULATED, scoreOf(20.0),
                NEVER_RECALCULATED, scoreOf(70.0)));

        var entries = service(true).handle(new GetGroupLeaderboardQuery(GROUP, RECALCULATED));

        // Gana quien tiene el PeerScore fresco mas alto, no quien arrastraba mas
        // puntos del contador anterior ni quien ya tenia una fila guardada.
        assertThat(entries).extracting("userId").containsExactly(NEVER_RECALCULATED, RECALCULATED);
        assertThat(entries).extracting("score").containsExactly(70, 20);
    }

    @Test
    void withPeerScoreOffTheWholeTableFallsBackToTheOldCounter() {
        // Apagar el motor tiene que devolver la tabla entera a la escala vieja,
        // no dejar una fila en cada una.
        givenMembers(RECALCULATED, NEVER_RECALCULATED);
        givenReputation(RECALCULATED, 12, 50.0);
        givenReputation(NEVER_RECALCULATED, 40, 50.0);

        var entries = service(false).handle(new GetGroupLeaderboardQuery(GROUP, RECALCULATED));

        assertThat(entries).extracting("score").containsExactly(40, 12);
        // Con el motor apagado no se recalcula nada: es lo que permite volver
        // atras sin que la pantalla dispare trabajo del motor nuevo.
        verify(peerScoreService, never()).recalculateFor(any());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private void givenMembers(long... userIds) {
        var group = new Group();
        ReflectionTestUtils.setField(group, "id", GROUP);
        var members = new java.util.ArrayList<GroupMember>();
        for (long userId : userIds) {
            members.add(new GroupMember(group, user(userId), GroupRole.MEMBER));
        }
        when(groupMemberRepository.findAllByGroupId(GROUP)).thenReturn(members);
        // El ranking es privado del grupo: quien lo pide tiene que ser miembro.
        when(groupMemberRepository.findByGroupIdAndUser_Id(anyLong(), anyLong()))
                .thenReturn(Optional.of(members.get(0)));
        lenient().when(userInformationRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        lenient().when(userBadgeRepository.countByUser_Id(anyLong())).thenReturn(0L);
        lenient().when(reputationEventRepository
                .findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtDesc(anyLong(), any()))
                .thenReturn(List.of());
    }

    private void givenReputation(long userId, int legacyScore, Double peerScore) {
        var reputation = new UserReputation(user(userId));
        ReflectionTestUtils.setField(reputation, "score", legacyScore);
        if (peerScore != null) {
            ReflectionTestUtils.setField(reputation, "peerScore", peerScore);
            ReflectionTestUtils.setField(reputation, "peerLevel", ReputationLevel.NEW);
            ReflectionTestUtils.setField(reputation, "bandLow", 13.5);
            ReflectionTestUtils.setField(reputation, "bandHigh", 86.5);
        }
        when(userReputationRepository.findByUser_Id(userId)).thenReturn(Optional.of(reputation));
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
    }

    private void givenFreshScores(Map<Long, ScoreResult> results) {
        when(peerScoreService.recalculateFor(any())).thenReturn(results);
    }

    private ScoreResult scoreOf(double score) {
        return new ScoreResult(score, 13.5, 86.5, ReputationLevel.NEW, 0.0, 0, 2.0, 2.0, List.of());
    }

    private User user(long userId) {
        var user = new User("user" + userId, "secret");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
