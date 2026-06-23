package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ExpenseResource;

public class ExpenseResourceFromEntityAssembler {
    public static ExpenseResource toResourceFromEntity(Expense expense){
        return toResourceFromEntity(expense, "");
    }

    public static ExpenseResource toResourceFromEntity(Expense expense, String blockchainHash){
        return new ExpenseResource(
                expense.getId(),
                expense.getName(),
                expense.getAmount(),
                expense.getUser().getId(),
                expense.getGroup().getId(),
                expense.getDueDate(),
                expense.getRemainingAmount(),
                expense.getTotalPaidAmount(),
                expense.getStatus(),
                expense.getActive(),
                blockchainHash,
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
