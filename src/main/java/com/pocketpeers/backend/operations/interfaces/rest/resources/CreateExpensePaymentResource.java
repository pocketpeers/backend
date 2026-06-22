package com.pocketpeers.backend.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateExpensePaymentResource(
        @NotNull
        String description,
        @NotNull
        BigDecimal amount,
        @NotNull
        Long userId
) {
}
