package com.pocketpeers.backend.pbl.application.internal.queryservices;

import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.entities.BadgeCatalog;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.entities.UserBadge;
import com.pocketpeers.backend.pbl.domain.model.queries.GetGroupLeaderboardQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetReputationHistoryQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetUserReputationQuery;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import com.pocketpeers.backend.pbl.domain.services.PblQueryService;
import com.pocketpeers.backend.pbl.domain.services.PeerScoreService;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.BadgeCatalogRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserBadgeRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.LeaderboardEntryResource;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ReputationResource;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ScoreSeriesPointResource;
import com.pocketpeers.backend.pbl.interfaces.rest.transform.ReputationResourceFromEntityAssembler;
import com.pocketpeers.backend.pbl.interfaces.rest.transform.ScoreSeriesPointResourceFromEntityAssembler;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PblQueryServiceImpl implements PblQueryService {
    private final UserRepository userRepository;
    private final UserReputationRepository userReputationRepository;
    private final ReputationEventRepository reputationEventRepository;
    private final BadgeCatalogRepository badgeCatalogRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserInformationRepository userInformationRepository;
    private final PeerScoreService peerScoreService;
    private final PeerScoreProperties peerScoreProperties;

    public PblQueryServiceImpl(UserRepository userRepository, UserReputationRepository userReputationRepository,
                               ReputationEventRepository reputationEventRepository,
                               BadgeCatalogRepository badgeCatalogRepository, UserBadgeRepository userBadgeRepository,
                               GroupMemberRepository groupMemberRepository,
                               UserInformationRepository userInformationRepository,
                               PeerScoreService peerScoreService, PeerScoreProperties peerScoreProperties) {
        this.userRepository = userRepository;
        this.userReputationRepository = userReputationRepository;
        this.reputationEventRepository = reputationEventRepository;
        this.badgeCatalogRepository = badgeCatalogRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userInformationRepository = userInformationRepository;
        this.peerScoreService = peerScoreService;
        this.peerScoreProperties = peerScoreProperties;
    }

    @Override
    public UserReputation handle(GetUserReputationQuery query) {
        // Reputation is created lazily so older users or freshly registered
        // users can be shown in PBL screens before receiving their first event.
        var user = userRepository.findById(query.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        return userReputationRepository.findByUser_Id(query.userId()).orElseGet(() -> userReputationRepository.save(new UserReputation(user)));
    }

    @Override
    public ReputationResource getUserReputationResource(Long userId) {
        // Se recalcula al leer en vez de servir el ultimo valor guardado, por dos
        // razones. El desglose explicativo no se persiste, y el score decae de
        // forma continua: un valor de hace horas afirmaria una confianza que ya
        // cambio. El recalculo deja ademas la fila al dia, igual que este mismo
        // servicio ya crea la reputacion que falta cuando alguien la consulta.
        var result = peerScoreService.recalculate(userId);
        var reputation = handle(new GetUserReputationQuery(userId));
        return ReputationResourceFromEntityAssembler.toResourceFromEntity(
                reputation, result, peerScoreService.goalFor(result), peerScoreProperties.isEnabled());
    }

    @Override
    public List<ReputationEvent> handle(GetReputationHistoryQuery query) {
        return reputationEventRepository.findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtAsc(
                query.userId(), LocalDateTime.now().minusDays(query.days()));
    }

    @Override
    public List<ScoreSeriesPointResource> getScoreSeries(Long userId, int days, int points) {
        // Con el motor nuevo apagado no hay serie que reconstruir: el score que
        // se muestra es entonces el contador PBL, y ese ya viene punto a punto en
        // el `resultingScore` de cada evento del historial. Devolver una serie
        // calculada con PeerScore mientras la tarjeta muestra el contador seria
        // volver a tener dos numeros distintos para lo mismo, que es justo lo que
        // esta serie existe para evitar.
        if (!peerScoreProperties.isEnabled()) {
            return List.of();
        }
        return peerScoreService.scoreSeries(userId, days, points).stream()
                .map(ScoreSeriesPointResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
    }

    @Override
    public List<UserBadge> getUserBadges(Long userId) {
        return userBadgeRepository.findAllByUser_Id(userId);
    }

    @Override
    public List<BadgeCatalog> getAllBadges() {
        return badgeCatalogRepository.findAll();
    }

    @Override
    public List<LeaderboardEntryResource> handle(GetGroupLeaderboardQuery query) {
        // Leaderboards are group-private: the requester must be a member before
        // seeing other members' public reputation data.
        if (!groupMemberRepository.findByGroupIdAndUser_Id(query.groupId(), query.viewerUserId()).isPresent()) {
            throw new RuntimeException("Access denied to group leaderboard");
        }
        var members = groupMemberRepository.findAllByGroupId(query.groupId());

        // La escala se decide una sola vez para toda la tabla, no fila por fila.
        //
        // Antes cada miembro elegia la suya segun si tenia un PeerScore
        // guardado, y eso ponia en la misma columna dos unidades que no son
        // comparables: la estimacion de PeerScore, acotada en [0, 100] y anclada
        // en 50 cuando no hay evidencia, junto al contador viejo, que son puntos
        // acumulados y arranca en 0. Un usuario recien recalculado aparecia con
        // 50 al lado de uno con 3, y el orden decia que el primero era mucho
        // mejor cuando en realidad no se habia comparado nada.
        //
        // Peor: consultar el perfil publico de alguien persiste su PeerScore, o
        // sea que la fila cambiaba de escala sola. El ranking dependia de que
        // perfiles hubiera abierto el usuario antes de mirarlo.
        //
        // Recalcular a todo el grupo de una vez cuesta una consulta por miembro
        // sobre estadisticas ya cacheadas, y es lo que la pantalla necesita de
        // todos modos: el score decae de forma continua, asi que servir el
        // ultimo valor guardado afirma una confianza que ya cambio. Es la misma
        // razon por la que getUserReputationResource recalcula al leer.
        var freshScores = peerScoreProperties.isEnabled()
                ? peerScoreService.recalculateFor(
                        members.stream().map(member -> member.getUser().getId()).toList())
                : Map.<Long, ScoreResult>of();

        var entries = members.stream()
                .map(member -> {
                    var userId = member.getUser().getId();
                    var reputation = handle(new GetUserReputationQuery(userId));
                    // Si el recalculo no devolvio a este usuario, el motor esta
                    // apagado o el usuario desaparecio entre una consulta y otra.
                    // En los dos casos toda la tabla cae al contador viejo, que es
                    // la unica escala que todos comparten.
                    var peerScore = freshScores.get(userId);
                    var usePeerScore = freshScores.size() == members.size() && peerScore != null;
                    return new DraftLeaderboardEntry(
                            userId,
                            displayName(userId, member.getUser().getUsername()),
                            photo(userId),
                            usePeerScore ? peerScore.score() : reputation.getScore(),
                            (usePeerScore ? peerScore.level() : reputation.getLevel()).getDisplayName(),
                            userBadgeRepository.countByUser_Id(userId),
                            userId.equals(query.viewerUserId()),
                            trend(userId),
                            peerScore == null ? null : peerScore.score(),
                            peerScore == null ? null : peerScore.bandLow(),
                            peerScore == null ? null : peerScore.bandHigh()
                    );
                })
                .sorted(Comparator.comparingDouble(DraftLeaderboardEntry::score).reversed()
                        .thenComparing(Comparator.comparingLong(DraftLeaderboardEntry::unlockedBadges).reversed()))
                .toList();

        // Rank is assigned after sorting so ties still receive a deterministic
        // position based on score first and unlocked badges second.
        var counter = new AtomicInteger(0);
        return entries.stream()
                .map(entry -> new LeaderboardEntryResource(entry.userId(), entry.fullName(), entry.photo(),
                        counter.incrementAndGet(), (int) Math.round(entry.score()), entry.level(),
                        entry.unlockedBadges(), entry.currentUser(), entry.trend(),
                        entry.peerScore(), entry.bandLow(), entry.bandHigh()))
                .toList();
    }

    private String displayName(Long userId, String fallback) {
        return userInformationRepository.findByUserId(userId).map(UserInformation::getFullName).orElse(fallback);
    }

    private String photo(Long userId) {
        return userInformationRepository.findByUserId(userId).map(UserInformation::getPhoto).orElse("");
    }

    private String trend(Long userId) {
        // Trend is intentionally simple for the mobile UI: only the net score
        // movement from the last seven days is exposed.
        var events = reputationEventRepository.findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtDesc(userId, LocalDateTime.now().minusDays(7));
        if (events.isEmpty()) return "STABLE";
        var delta = events.stream().mapToInt(ReputationEvent::getPointsDelta).sum();
        if (delta > 0) return "UP";
        if (delta < 0) return "DOWN";
        return "STABLE";
    }

    private record DraftLeaderboardEntry(Long userId, String fullName, String photo, double score, String level,
                                         long unlockedBadges, boolean currentUser, String trend,
                                         Double peerScore, Double bandLow, Double bandHigh) {
    }
}
