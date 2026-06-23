package com.pocketpeers.backend.groups.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverduePaymentDebtResource(
        Long paymentId,
        Long expenseId,
        String expenseName,
        String groupName,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal overdueAmount,
        LocalDate dueDate,
        long daysOverdue,
        String status,
        Boolean confirmed
) {
}
