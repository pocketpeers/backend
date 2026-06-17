package com.pocketpeers.backend.operations.domain.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private final ExpensesNotificationService expensesNotificationService;

    public ScheduledTasks(ExpensesNotificationService expensesNotificationService) {
        this.expensesNotificationService = expensesNotificationService;
    }

    @Scheduled(cron = "0 0 8 * * ?", zone = "America/Lima")
    public void sendDailyPaymentReminders() {
        expensesNotificationService.createPaymentReminders();
    }
}
