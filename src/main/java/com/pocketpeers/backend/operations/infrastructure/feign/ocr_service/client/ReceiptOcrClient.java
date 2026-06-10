package com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.client;

import com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.dto.ReceiptOcrRequest;
import com.pocketpeers.backend.operations.infrastructure.feign.ocr_service.dto.ReceiptOcrResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "receipt-ocr-client", url = "${feign.ocr.service.url}")
public interface ReceiptOcrClient {

    @PostMapping("/receipt-ocr")
    ReceiptOcrResponse ocrReceiptForImagePath(ReceiptOcrRequest request);
}
