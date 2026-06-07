package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptOcrResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptResource;

public class ReceiptResourceFromEntityAssembler {
    public static ReceiptResource toResourceFromEntity(Receipt receipt){
        return new ReceiptResource(
                receipt.getId(),
                receipt.getName(),
                receipt.getReceiptNumber(),
                receipt.getAmount().amount(),
                receipt.getIssueDate(),
                receipt.getImagePath()
        );
    }

    public static ReceiptOcrResource toResourceFromEntity(OcrReceipt receipt) {
        return new ReceiptOcrResource(
                receipt.getId(),
                receipt.getName(),
                receipt.getReceiptNumber(),
                receipt.getAmount().amount(),
                receipt.getIssueDate(),
                receipt.getImagePath(),
                receipt.getOcrData().dataFields()
        );
    }
}
