package com.pocketpeers.backend.operations.interfaces.rest.resources;


import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptResource(
        Long id,
        String name,
        String receiptNumber,
        BigDecimal amount,
        LocalDate issueDate,
        String imagePath
) {
}
