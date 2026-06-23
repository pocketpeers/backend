package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentReminderResource;

public class PaymentReminderResourceFromEntityAssembler {
    public static PaymentReminderResource toResourceFromEntity(PaymentReminder reminder) {
        var payment = reminder.getPayment();
        var expense = payment.getExpense();
        var group = expense.getGroup();
        return new PaymentReminderResource(
                reminder.getId(),
                payment.getId(),
                expense.getId(),
                group.getId(),
                group.getName(),
                reminder.getType(),
                reminder.getTitle(),
                reminder.getBody(),
                reminder.getCreatedAt()
        );
    }
}
