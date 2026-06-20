package com.pocketpeers.backend.operations.interfaces.rest.resources;

import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;

import java.util.Date;

public record PaymentReminderResource(
        Long id,
        Long paymentId,
        Long expenseId,
        Long groupId,
        String groupName,
        PaymentReminderType type,
        String title,
        String body,
        Date createdAt
) {
}
