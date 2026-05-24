package com.pocketpeers.backend.operations.domain.exceptions;

public class ReceiptNotFoundException extends RuntimeException {
    public ReceiptNotFoundException(Long receiptId) {
        super("Receipt with id:" + receiptId+ " not found");
    }
}
