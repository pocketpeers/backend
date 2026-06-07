package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ReceiptOcrResource(
        Long id,
        String name,
        String receiptNumber,
        BigDecimal amount,
        LocalDate issueDate,
        String imagePath,
        Map<String,Object> dataFields
) {
}
