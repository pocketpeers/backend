package com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.mapper;

import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.model.valueobjects.OcrData;
import com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.dto.ReceiptOcrRequest;
import com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.dto.ReceiptOcrResponse;

public class ReceiptOcrMapper {
    static public OcrReceipt mapToEntity(ReceiptOcrResponse response){
        return new OcrReceipt(
                response.name(),
                response.receiptNumber(),
                response.issuerRuc(),
                response.amount(),
                response.issueDate(),
                response.imagePath(),
                new OcrData(response.dataFields())
        );
    }
    static public ReceiptOcrRequest mapToRequest(String imagePath) {
        return new ReceiptOcrRequest(imagePath);
    }
}
