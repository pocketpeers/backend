package com.pocketpeers.backend.pbl.application.internal.commandservices;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.entities.BadgeCatalog;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.entities.UserBadge;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.BadgeCatalogRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserBadgeRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class PblCommandServiceImpl implements PblCommandService {
    private final UserRepository userRepository;
    private final UserReputationRepository userReputationRepository;
    private final ReputationEventRepository reputationEventRepository;
    private final BadgeCatalogRepository badgeCatalogRepository;
    private final UserBadgeRepository userBadgeRepository;

    public PblCommandServiceImpl(UserRepository userRepository, UserReputationRepository userReputationRepository,
                                 ReputationEventRepository reputationEventRepository,
                                 BadgeCatalogRepository badgeCatalogRepository, UserBadgeRepository userBadgeRepository) {
        this.userRepository = userRepository;
        this.userReputationRepository = userReputationRepository;
        this.reputationEventRepository = reputationEventRepository;
        this.badgeCatalogRepository = badgeCatalogRepository;
        this.userBadgeRepository = userBadgeRepository;
    }

    @Override
    @Transactional
    public Long handle(RegisterReputationEventCommand command) {
        var user = userRepository.findById(command.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        var reputation = userReputationRepository.findByUser_Id(command.userId()).orElseGet(() -> new UserReputation(user));
        var delta = calculateDelta(command.type(), reputation.getOnTimePaymentStreak());
        var resultingScore = reputation.applyDelta(delta);
        if (command.type() == ReputationEventType.ON_TIME_PAYMENT) reputation.registerOnTimePayment();
        if (command.type() == ReputationEventType.PARTIAL_PAYMENT) reputation.registerPartialPayment();
        if (command.type() == ReputationEventType.LATE_PAYMENT) reputation.registerLatePayment();
        userReputationRepository.save(reputation);
        unlockBadges(reputation);
        var event = new ReputationEvent(user, command.groupId(), command.paymentId(), command.type(), delta,
                resultingScore, command.description());
        return reputationEventRepository.save(event).getId();
    }

    @Override
    public void seedDefaultBadges() {
        createBadgeIfMissing("FIRST_PAYMENT", "Primer pago", "Completo su primer pago registrado.");
        createBadgeIfMissing("STREAK_3", "Racha x3", "Completo tres pagos puntuales consecutivos.");
        createBadgeIfMissing("SILVER_LEVEL", "Reputacion Plata", "Alcanzo el nivel Plata.");
        createBadgeIfMissing("GOLD_LEVEL", "Reputacion Oro", "Alcanzo el nivel Oro.");
    }

    private int calculateDelta(ReputationEventType type, int currentStreak) {
        return switch (type) {
            case ON_TIME_PAYMENT -> 5 + Math.max(0, 3 - (currentStreak / 2));
            case PARTIAL_PAYMENT -> 3;
            case LATE_PAYMENT -> -8;
            case MANUAL_ADJUSTMENT -> 0;
        };
    }

    private void unlockBadges(UserReputation reputation) {
        unlockIf(reputation, "FIRST_PAYMENT", reputation.getCompletedPayments() >= 1);
        unlockIf(reputation, "STREAK_3", reputation.getOnTimePaymentStreak() >= 3);
        unlockIf(reputation, "SILVER_LEVEL", reputation.getScore() >= 60);
        unlockIf(reputation, "GOLD_LEVEL", reputation.getScore() >= 85);
    }

    private void unlockIf(UserReputation reputation, String code, boolean condition) {
        if (!condition || userBadgeRepository.existsByUser_IdAndBadge_Code(reputation.getUser().getId(), code)) return;
        badgeCatalogRepository.findByCode(code)
                .ifPresent(badge -> userBadgeRepository.save(new UserBadge(reputation.getUser(), badge)));
    }

    private void createBadgeIfMissing(String code, String name, String description) {
        if (!badgeCatalogRepository.existsByCode(code)) {
            badgeCatalogRepository.save(new BadgeCatalog(code, name, description));
        }
    }
}
