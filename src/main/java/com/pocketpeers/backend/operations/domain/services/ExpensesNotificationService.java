package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import com.pocketpeers.backend.operations.infrastructure.notifications.FcmNotificationService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentReminderRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class ExpensesNotificationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PaymentRepository paymentRepository;
    private final PaymentReminderRepository reminderRepository;
    private final FcmNotificationService fcmNotificationService;

    public ExpensesNotificationService(
            PaymentRepository paymentRepository,
            PaymentReminderRepository reminderRepository,
            FcmNotificationService fcmNotificationService
    ) {
        this.paymentRepository = paymentRepository;
        this.reminderRepository = reminderRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    @Transactional
    public int createPaymentReminders() {
        LocalDate today = LocalDate.now();
        var created = 0;

        for (Payment payment : paymentRepository.findUnpaidPaymentsDueOn(today.plusDays(2))) {
            created += createReminderIfMissing(payment, PaymentReminderType.DUE_IN_48_HOURS);
        }

        for (Payment payment : paymentRepository.findUnpaidPaymentsDueOn(today)) {
            created += createReminderIfMissing(payment, PaymentReminderType.DUE_TODAY);
        }

        return created;
    }

    private int createReminderIfMissing(Payment payment, PaymentReminderType type) {
        if (reminderRepository.existsByPayment_IdAndType(payment.getId(), type)) {
            return 0;
        }

        var expense = payment.getExpense();
        var group = expense.getGroup();
        var dueDate = expense.getDueDate();
        var pendingAmount = payment.getAmount().subtract(payment.getAmountPaid());
        var title = switch (type) {
            case DUE_IN_48_HOURS -> "Tu pago vence en 48 horas";
            case DUE_TODAY -> "Tu pago vence hoy";
        };
        var body = switch (type) {
            case DUE_IN_48_HOURS -> "Grupo " + group.getName()
                    + ": tienes S/ " + formatAmount(pendingAmount)
                    + " pendientes hasta el " + dueDate.format(DATE_FORMATTER) + ".";
            case DUE_TODAY -> "Grupo " + group.getName()
                    + ": tu pago de S/ " + formatAmount(pendingAmount)
                    + " vence hoy (" + dueDate.format(DATE_FORMATTER)
                    + "). Un pago tardio afectara tu score de reputacion.";
        };

        var reminder = reminderRepository.save(new PaymentReminder(payment.getUser(), payment, type, title, body));
        fcmNotificationService.sendPaymentReminder(reminder);
        return 1;
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
