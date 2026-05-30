package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResource(Long id,
                              String name,
                              BigDecimal amount,
                              Long userId,
                              Long groupId,
                              LocalDate dueDate,
                              BigDecimal remainingAmount,
                              BigDecimal paidAmount,
                              String status,
                              java.util.Date createdAt,
                              java.util.Date updatedAt) {
}
