package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
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
    private final PaymentCommandService paymentCommandService;

    public ScheduledTasks(ExpensesNotificationService expensesNotificationService,
                          PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          PblCommandService pblCommandService,
                          PaymentCommandService paymentCommandService) {
        this.expensesNotificationService = expensesNotificationService;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.pblCommandService = pblCommandService;
        this.paymentCommandService = paymentCommandService;
    }

    @Scheduled(cron = "0 0 8 * * ?", zone = "America/Lima")
    public void sendDailyPaymentReminders() {
        expensesNotificationService.createPaymentReminders();
    }

    // Overdue penalties run just after midnight, when the due date has actually
    // closed, and no longer ride along with the 08:00 reminders. Bundled with
    // them they left an eight hour window in which a payment was already overdue
    // on screen but still unpenalised, so the score disagreed with the dates the
    // group could see. Reminders stay at 08:00 because they push notifications
    // and nobody wants one at midnight; registering a penalty is silent.
    //
    // What to register is decided by the payment service: the reputation event
    // now carries the creditor, the amount and the deadline, and those are read
    // from the payment and its expense, not from the clock in this class.
    @Scheduled(cron = "0 5 0 * * ?", zone = "America/Lima")
    public void registerOverduePaymentPenalties() {
        paymentCommandService.registerOverduePenalties();
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
                pblCommandService.handle(RegisterReputationEventCommand.badgeOnly(
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
