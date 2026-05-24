package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateExpenseCommand(Long id, String name, BigDecimal amount, LocalDate dueDate) {
}
