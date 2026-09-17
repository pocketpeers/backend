package com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ReceiptOcrResponse(
        String imagePath,
        BigDecimal amount,
        String name,
        LocalDate issueDate,
        String receiptNumber,
        String issuerRuc,
        Map<String, Object> dataFields

) {
}
