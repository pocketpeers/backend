package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;
import java.util.List;

public record PaymentResource(Long id,
                              String description,
                              BigDecimal amount,
                              BigDecimal amountPaid,
                              String status,
                              Boolean confirmed,
                              Long userId,
                              Long expenseId,
                              String blockchainHash,
                              List<String> evidencePhotos
) {
}
