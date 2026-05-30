package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateReceiptForExpenseCommand(
        String name,
        String receiptNumber,
        BigDecimal amount,
        LocalDate issueDate,
        String imagePath,
        Long expenseId
) {
}
