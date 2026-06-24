package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class ScheduledTasks {
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final ExpensesNotificationService expensesNotificationService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PblCommandService pblCommandService;
    private final ReputationEventRepository reputationEventRepository;

    public ScheduledTasks(ExpensesNotificationService expensesNotificationService,
                          PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          PblCommandService pblCommandService,
                          ReputationEventRepository reputationEventRepository) {
        this.expensesNotificationService = expensesNotificationService;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.pblCommandService = pblCommandService;
        this.reputationEventRepository = reputationEventRepository;
    }

    @Scheduled(cron = "0 0 8 * * ?", zone = "America/Lima")
    public void sendDailyPaymentReminders() {
        expensesNotificationService.createPaymentReminders();
        registerOverduePaymentPenalties();
    }

    private void registerOverduePaymentPenalties() {
        var today = LocalDate.now(LIMA_ZONE);
        for (var payment : paymentRepository.findOverdueUnpaidPayments(today)) {
            registerOverduePaymentPenaltyIfMissing(payment.getId(), payment.getUser().getId(),
                    payment.getExpense().getGroup().getId());
        }
    }

    private void registerOverduePaymentPenaltyIfMissing(Long paymentId, Long userId, Long groupId) {
        if (reputationEventRepository.existsByPaymentIdAndType(paymentId, ReputationEventType.OVERDUE_PAYMENT)) {
            return;
        }
        pblCommandService.handle(new RegisterReputationEventCommand(
                userId,
                groupId,
                paymentId,
                ReputationEventType.OVERDUE_PAYMENT,
                "Payment became overdue before being completed"
        ));
    }

    @Scheduled(cron = "0 55 23 * * ?", zone = "America/Lima")
    public void unlockZeroDebtBadgesAtMonthClose() {
        var today = LocalDate.now(LIMA_ZONE);
        if (today.plusDays(1).getDayOfMonth() != 1) {
            return;
        }

        var monthStart = today.withDayOfMonth(1);
        userRepository.findAll().forEach(user -> {
            var hasPaymentsDueThisMonth = paymentRepository.existsPaymentDueForUserBetween(
                    user.getId(), monthStart, today);
            var hasPendingDebt = paymentRepository.countPendingPaymentsByUserIdDueOnOrBefore(
                    user.getId(), today) > 0;
            if (hasPaymentsDueThisMonth && !hasPendingDebt) {
                pblCommandService.handle(new RegisterReputationEventCommand(
                        user.getId(),
                        null,
                        null,
                        ReputationEventType.ZERO_DEBT,
                        "Month closed with no pending debts or commitments"
                ));
            }
        });
    }
}
