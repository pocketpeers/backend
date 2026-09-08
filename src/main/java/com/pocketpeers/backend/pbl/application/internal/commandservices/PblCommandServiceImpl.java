package com.pocketpeers.backend.pbl.application.internal.commandservices;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.entities.BadgeCatalog;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.entities.UserBadge;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.pbl.domain.services.PeerScoreService;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.BadgeCatalogRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserBadgeRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class PblCommandServiceImpl implements PblCommandService {
    // Central scoring table for PBL events. Keeping the values in one place
    // makes reputation changes easier to audit when business rules change.
    private static final int ON_TIME_PAYMENT_POINTS = 3;
    private static final int PARTIAL_PAYMENT_POINTS = 1;
    private static final int OVERDUE_PAYMENT_POINTS = -6;
    private static final int LATE_PAYMENT_POINTS = 1;
    private static final int GROUP_CREATED_POINTS = 0;

    private final UserRepository userRepository;
    private final UserReputationRepository userReputationRepository;
    private final ReputationEventRepository reputationEventRepository;
    private final BadgeCatalogRepository badgeCatalogRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final PeerScoreService peerScoreService;
    private final PeerScoreProperties peerScoreProperties;

    public PblCommandServiceImpl(UserRepository userRepository, UserReputationRepository userReputationRepository,
                                 ReputationEventRepository reputationEventRepository,
                                 BadgeCatalogRepository badgeCatalogRepository, UserBadgeRepository userBadgeRepository,
                                 PeerScoreService peerScoreService, PeerScoreProperties peerScoreProperties) {
        this.userRepository = userRepository;
        this.userReputationRepository = userReputationRepository;
        this.reputationEventRepository = reputationEventRepository;
        this.badgeCatalogRepository = badgeCatalogRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.peerScoreService = peerScoreService;
        this.peerScoreProperties = peerScoreProperties;
    }

    @Override
    @Transactional
    public Long handle(RegisterReputationEventCommand command) {
        // A reputation event is the source of truth for why a user's score
        // changed. The aggregate stores the current counters, while the event
        // table preserves the historical reason and resulting score.
        var user = userRepository.findById(command.userId()).orElseThrow(() -> new RuntimeException("User not found"));
        var reputation = userReputationRepository.findByUser_Id(command.userId()).orElseGet(() -> new UserReputation(user));
        var delta = calculateDelta(command.type());
        var resultingScore = reputation.applyDelta(delta);
        if (command.type() == ReputationEventType.ON_TIME_PAYMENT) reputation.registerOnTimePayment();
        if (command.type() == ReputationEventType.PARTIAL_PAYMENT) reputation.registerPartialPayment();
        if (command.type() == ReputationEventType.OVERDUE_PAYMENT) reputation.registerOverduePayment();
        if (command.type() == ReputationEventType.LATE_PAYMENT) reputation.registerLatePayment();
        userReputationRepository.save(reputation);

        var event = new ReputationEvent(user, command.groupId(), command.paymentId(), command.type(), delta,
                resultingScore, command.description(), command.counterpartyId(), command.amount(),
                command.dueAt(), command.resolvedAt());
        var eventId = reputationEventRepository.save(event).getId();

        // PeerScore no acumula: recalcula desde el historial completo, asi que
        // solo puede correr una vez que este evento ya esta guardado.
        peerScoreService.recalculateAfterEvent(command.userId(), command.counterpartyId());

        // Las insignias se evaluan al final, sobre el agregado que los dos
        // motores ya actualizaron: las de nivel dependen del score y leerlas
        // antes del recalculo las dejaria un evento por detras.
        var recalculated = userReputationRepository.findByUser_Id(command.userId()).orElse(reputation);
        unlockBadges(recalculated, command.type());

        return eventId;
    }

    @Override
    @Transactional
    public void refreshLevelBadges(Long userId) {
        userReputationRepository.findByUser_Id(userId).ifPresent(this::unlockLevelBadges);
    }

    @Override
    public void seedDefaultBadges() {
        // Seed is idempotent so it can be called from local setup or admin
        // tooling without duplicating catalog badges.
        createBadgeIfMissing("FIRST_PAYMENT", "Primer pago", "Completo su primer pago registrado.");
        createBadgeIfMissing("STREAK_3", "Racha x3", "Completo tres pagos puntuales consecutivos.");
        createBadgeIfMissing("STREAK_10", "Racha x10", "Completo diez pagos puntuales consecutivos.");
        createBadgeIfMissing("STREAK_50", "Racha x50", "Completo cincuenta pagos puntuales consecutivos.");
        createBadgeIfMissing("STREAK_100", "Racha x100", "Completo cien pagos puntuales consecutivos.");
        createBadgeIfMissing("EARLY_BIRD", "Madrugador", "Realizó un pago con más de 48 horas de anticipación.");
        createBadgeIfMissing("GROUP_FOUNDER", "Líder de Grupo", "Creó un grupo colaborativo de microfinanzas con éxito.");
        createBadgeIfMissing("JUST_IN_TIME", "En el último segundo", "Realizó el pago faltando menos de una hora para el cierre del gasto.");
        createBadgeIfMissing("PARTIAL_EFFORT", "Paso a Paso", "Realizó su primer pago parcial, demostrando compromiso con su saldo.");
        createBadgeIfMissing("ZERO_DEBT", "Historial Limpio", "Cerró el mes con cero deudas o compromisos pendientes.");
        createBadgeIfMissing("SILVER_LEVEL", "Reputacion Plata", "Alcanzo el nivel Plata.");
        createBadgeIfMissing("GOLD_LEVEL", "Reputacion Oro", "Alcanzo el nivel Oro.");
    }

    private int calculateDelta(ReputationEventType type) {
        return switch (type) {
            case ON_TIME_PAYMENT -> ON_TIME_PAYMENT_POINTS;
            case PARTIAL_PAYMENT -> PARTIAL_PAYMENT_POINTS;
            case OVERDUE_PAYMENT -> OVERDUE_PAYMENT_POINTS;
            case LATE_PAYMENT -> LATE_PAYMENT_POINTS;
            case GROUP_CREATED -> GROUP_CREATED_POINTS;
            case MANUAL_ADJUSTMENT, EARLY_PAYMENT, JUST_IN_TIME_PAYMENT, ZERO_DEBT -> 0;
        };
    }

    private void unlockBadges(UserReputation reputation, ReputationEventType eventType) {
        // Badge unlock rules use both cumulative aggregate counters and the
        // current event type, because some badges are one-off achievements.
        unlockIf(reputation, "FIRST_PAYMENT", reputation.getCompletedPayments() >= 1);
        unlockIf(reputation, "STREAK_3", reputation.getOnTimePaymentStreak() >= 3);
        unlockIf(reputation, "STREAK_10", reputation.getOnTimePaymentStreak() >= 10);
        unlockIf(reputation, "STREAK_50", reputation.getOnTimePaymentStreak() >= 50);
        unlockIf(reputation, "STREAK_100", reputation.getOnTimePaymentStreak() >= 100);
        unlockIf(reputation, "EARLY_BIRD", eventType == ReputationEventType.EARLY_PAYMENT);
        unlockIf(reputation, "GROUP_FOUNDER", eventType == ReputationEventType.GROUP_CREATED);
        unlockIf(reputation, "JUST_IN_TIME", eventType == ReputationEventType.JUST_IN_TIME_PAYMENT);
        unlockIf(reputation, "PARTIAL_EFFORT", eventType == ReputationEventType.PARTIAL_PAYMENT);
        unlockIf(reputation, "ZERO_DEBT", eventType == ReputationEventType.ZERO_DEBT);
        unlockLevelBadges(reputation);
    }

    private void unlockLevelBadges(UserReputation reputation) {
        unlockIf(reputation, "SILVER_LEVEL", hasReached(reputation, ReputationLevel.SILVER));
        unlockIf(reputation, "GOLD_LEVEL", hasReached(reputation, ReputationLevel.GOLD));
    }

    /**
     * Si el usuario alcanzo un nivel, segun el motor que este activo.
     *
     * <p>Con PeerScore activo el nivel no sale del puntaje solo: exige tambien
     * una banda estrecha y contrapartes distintas. Por eso se compara el nivel ya
     * resuelto y no el numero: alguien con score 90 y una sola contraparte no
     * llego a Plata, y darle la insignia contradiria justamente la defensa que
     * hace inutil la colusion.</p>
     */
    private boolean hasReached(UserReputation reputation, ReputationLevel level) {
        if (peerScoreProperties.isEnabled() && reputation.hasPeerScore()) {
            return reputation.getPeerLevel().ordinal() >= level.ordinal();
        }
        return reputation.getScore() >= level.getMinimumScore();
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
