package com.pocketpeers.backend.operations.domain.model.commands;

public record DeleteExpenseCommand(Long expenseId, String username) {
}
