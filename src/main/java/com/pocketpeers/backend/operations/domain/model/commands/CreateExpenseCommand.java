package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseCommand(String name, BigDecimal amount, Long userId, Long groupId, LocalDate dueDate) {
}
