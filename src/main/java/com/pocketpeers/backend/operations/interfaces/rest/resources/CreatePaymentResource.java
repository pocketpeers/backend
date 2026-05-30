package com.pocketpeers.backend.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentResource(@NotNull
                                    String description,
                                    @NotNull
                                    BigDecimal amount,
                                    @NotNull
                                    Long userId,
                                    @NotNull
                                    Long expenseId
) {
}
