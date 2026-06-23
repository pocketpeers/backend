package com.pocketpeers.backend.operations.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResource(Long id,
                              String name,
                              BigDecimal amount,
                              Long userId,
                              Long groupId,
                              @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
                              LocalDate dueDate,
                              BigDecimal remainingAmount,
                              BigDecimal paidAmount,
                              String status,
                              Integer active,
                              String blockchainHash,
                              java.util.Date createdAt,
                              java.util.Date updatedAt) {
}
