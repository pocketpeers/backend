package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface ExpenseQueryService {
    List<Expense> handle(GetAllExpensesQuery query);
    Optional<Expense> handle(GetExpenseByIdQuery query);
    List<Expense> handle(GetAllExpensesByUserIdQuery query);

    /** Gastos donde el usuario participa: los que creo y aquellos donde debe. */
    List<Expense> handle(GetExpensesWhereUserParticipatesQuery query);
    Optional<Expense> handle(GetExpenseByNameAndUserIdQuery query);
    List<Expense> handle(SearchExpensesByNameQuery query);
    List<Expense> handle(GetAllExpensesByGroupIdQuery query);
    List<Expense> handle(GetAllExpensesByDueDate query);
}
