package com.pocketpeers.backend.operations.domain.model.events;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;

public record ExpenseCreatedEvent(
        Expense expense
) {
}
