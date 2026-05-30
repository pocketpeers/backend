package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesByDueDate;
import com.pocketpeers.backend.operations.infrastructure.twilio.TwilioSmsService;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@EnableScheduling
public class ExpensesNotificationService {

    private final ExpenseQueryService expenseQueryService;
    private final TwilioSmsService twilioSmsService;

    @Autowired
    public ExpensesNotificationService(ExpenseQueryService expenseQueryService, TwilioSmsService twilioSmsService) {
        this.expenseQueryService = expenseQueryService;
        this.twilioSmsService = twilioSmsService;
    }

    public void sendPaymentReminders() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);


        List<Expense> upcomingPayments = expenseQueryService.handle(new GetAllExpensesByDueDate(tomorrow));

        List<Expense> overduePayments = expenseQueryService.handle(new GetAllExpensesByDueDate(today));


        for (Expense expense : upcomingPayments) {
            sendReminder(expense, "Tu pago vence mañana: " + expense.getName() + " por " + expense.getAmount());
        }


        for (Expense expense : overduePayments) {
            sendReminder(expense, "Tu pago está atrasado: " + expense.getName() + " por " + expense.getAmount());
        }
    }

    private void sendReminder(Expense expense, String message) {
        UserInformation userInformation = expense.getUserInformation();
        String phoneNumber = userInformation.getPhoneNumber();

        twilioSmsService.sendReminder(phoneNumber, message);
    }
}
