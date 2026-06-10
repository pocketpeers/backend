package com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.dto;

public record ReceiptOcrRequest(
        String imageUrl
) {
}
