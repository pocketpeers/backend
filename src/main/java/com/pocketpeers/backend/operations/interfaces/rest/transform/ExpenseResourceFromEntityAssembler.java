package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ExpenseResource;

public class ExpenseResourceFromEntityAssembler {
    public static ExpenseResource toResourceFromEntity(Expense expense){
        return toResourceFromEntity(expense, "");
    }

    public static ExpenseResource toResourceFromEntity(Expense expense, String blockchainHash){
        return toResourceFromEntity(expense, blockchainHash, null);
    }

    /**
     * @param anchoredAt instante en que el gasto quedo escrito en la cadena.
     *                   Nulo mientras no lo este, igual que el hash.
     */
    public static ExpenseResource toResourceFromEntity(Expense expense,
                                                       String blockchainHash,
                                                       java.util.Date anchoredAt){
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
                anchoredAt,
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
