package com.pocketpeers.backend.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateExpenseWithPaymentsResource(
        @NotNull
        String name,
        @NotNull
        BigDecimal amount,
        @NotNull
        Long userId,
        @NotNull
        Long groupId,
        @NotNull
        LocalDate dueDate,
        @NotNull
        List<CreateExpensePaymentResource> payments
) {
}
