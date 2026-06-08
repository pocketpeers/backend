package com.pocketpeers.backend.operations.domain.model.queries;

public record GetExpenseByNameAndUserIdQuery(String expenseName, Long userId) {
}
