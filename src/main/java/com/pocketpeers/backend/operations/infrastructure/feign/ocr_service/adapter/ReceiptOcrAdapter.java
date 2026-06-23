package com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.adapter;

import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.ports.out.ReceiptOcrPort;
import com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.client.ReceiptOcrClient;
import com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.mapper.ReceiptOcrMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ReceiptOcrAdapter implements ReceiptOcrPort {
    private final  ReceiptOcrClient ocrServiceClient;

    @Override
    public OcrReceipt processReceiptImage(String imagePath) {
        var request = ReceiptOcrMapper.mapToRequest(imagePath);
        try {
            var response = ocrServiceClient.ocrReceiptForImagePath(request);
            return ReceiptOcrMapper.mapToEntity(response);
        } catch (Exception e) {
            // Handle exceptions appropriately, e.g., log the error or rethrow a custom exception
            throw new RuntimeException("Failed to process receipt image for OCR", e);
        }
    }
}
