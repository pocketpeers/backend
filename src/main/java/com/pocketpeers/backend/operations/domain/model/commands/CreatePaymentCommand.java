package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;

public record CreatePaymentCommand(String description, BigDecimal amount, Long userId, Long expenseId) {
}
