package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;

public record PaymentResource(Long id,
                              String description,
                              BigDecimal amount,
                              BigDecimal amountPaid,
                              String status,
                              Long userId,
                              Long expenseId
) {
}
