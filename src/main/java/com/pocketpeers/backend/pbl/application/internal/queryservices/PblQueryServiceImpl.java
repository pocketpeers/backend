package com.pocketpeers.backend.pbl.application.internal.queryservices;

import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.entities.BadgeCatalog;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.entities.UserBadge;
import com.pocketpeers.backend.pbl.domain.model.queries.GetGroupLeaderboardQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetReputationHistoryQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetUserReputationQuery;
import com.pocketpeers.backend.pbl.domain.services.PblQueryService;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.BadgeCatalogRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserBadgeRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.LeaderboardEntryResource;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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

    public PblQueryServiceImpl(UserRepository userRepository, UserReputationRepository userReputationRepository,
                               ReputationEventRepository reputationEventRepository,
                               BadgeCatalogRepository badgeCatalogRepository, UserBadgeRepository userBadgeRepository,
                               GroupMemberRepository groupMemberRepository,
                               UserInformationRepository userInformationRepository) {
        this.userRepository = userRepository;
        this.userReputationRepository = userReputationRepository;
        this.reputationEventRepository = reputationEventRepository;
        this.badgeCatalogRepository = badgeCatalogRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userInformationRepository = userInformationRepository;
    }

    @Override
    public UserReputation handle(GetUserReputationQuery query) {
        var user = userRepository.findById(query.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        return userReputationRepository.findByUser_Id(query.userId()).orElseGet(() -> userReputationRepository.save(new UserReputation(user)));
    }

    @Override
    public List<ReputationEvent> handle(GetReputationHistoryQuery query) {
        return reputationEventRepository.findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtAsc(
                query.userId(), LocalDateTime.now().minusDays(query.days()));
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
        if (!groupMemberRepository.findByGroupIdAndUser_Id(query.groupId(), query.viewerUserId()).isPresent()) {
            throw new RuntimeException("Access denied to group leaderboard");
        }
        var entries = groupMemberRepository.findAllByGroupId(query.groupId()).stream()
                .map(member -> {
                    var userId = member.getUser().getId();
                    var reputation = handle(new GetUserReputationQuery(userId));
                    return new DraftLeaderboardEntry(
                            userId,
                            displayName(userId, member.getUser().getUsername()),
                            photo(userId),
                            reputation.getScore(),
                            reputation.getLevel().getDisplayName(),
                            userBadgeRepository.countByUser_Id(userId),
                            userId.equals(query.viewerUserId()),
                            trend(userId)
                    );
                })
                .sorted(Comparator.comparingInt(DraftLeaderboardEntry::score).reversed()
                        .thenComparing(Comparator.comparingLong(DraftLeaderboardEntry::unlockedBadges).reversed()))
                .toList();

        var counter = new AtomicInteger(0);
        return entries.stream()
                .map(entry -> new LeaderboardEntryResource(entry.userId(), entry.fullName(), entry.photo(),
                        counter.incrementAndGet(), entry.score(), entry.level(), entry.unlockedBadges(),
                        entry.currentUser(), entry.trend()))
                .toList();
    }

    private String displayName(Long userId, String fallback) {
        return userInformationRepository.findByUserId(userId).map(UserInformation::getFullName).orElse(fallback);
    }

    private String photo(Long userId) {
        return userInformationRepository.findByUserId(userId).map(UserInformation::getPhoto).orElse("");
    }

    private String trend(Long userId) {
        var events = reputationEventRepository.findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtDesc(userId, LocalDateTime.now().minusDays(7));
        if (events.isEmpty()) return "STABLE";
        var delta = events.stream().mapToInt(ReputationEvent::getPointsDelta).sum();
        if (delta > 0) return "UP";
        if (delta < 0) return "DOWN";
        return "STABLE";
    }

    private record DraftLeaderboardEntry(Long userId, String fullName, String photo, int score, String level,
                                         long unlockedBadges, boolean currentUser, String trend) {
    }
}
