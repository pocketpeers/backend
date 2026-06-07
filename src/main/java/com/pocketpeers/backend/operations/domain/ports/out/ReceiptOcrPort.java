package com.pocketpeers.backend.operations.domain.ports.out;

import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;

public interface ReceiptOcrPort {
    OcrReceipt processReceiptImage(String imagePath);
}
