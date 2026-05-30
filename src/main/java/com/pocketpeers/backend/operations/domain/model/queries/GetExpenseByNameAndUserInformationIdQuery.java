package com.pocketpeers.backend.operations.domain.model.queries;

import com.pocketpeers.backend.operations.domain.model.valueobjects.ExpenseName;

public record GetExpenseByNameAndUserInformationIdQuery(ExpenseName expenseName, Long userInformationId) {
}
