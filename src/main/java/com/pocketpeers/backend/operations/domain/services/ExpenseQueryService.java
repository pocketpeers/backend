package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface ExpenseQueryService {
    List<Expense> handle(GetAllExpensesQuery query);
    Optional<Expense> handle(GetExpenseByIdQuery query);
    List<Expense> handle(GetAllExpensesByUserInformationIdQuery query);
    Optional<Expense> handle(GetExpenseByNameAndUserInformationIdQuery query);
    List<Expense> handle(GetAllExpensesByGroupIdQuery query);
    List<Expense> handle(GetAllExpensesByDueDate query);
}
