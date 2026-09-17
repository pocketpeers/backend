package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DuplicateReceiptCheck;
import com.pocketpeers.backend.operations.domain.model.valueobjects.OcrReceiptPreview;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptOcrResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptResource;

public class ReceiptResourceFromEntityAssembler {
    public static ReceiptResource toResourceFromEntity(Receipt receipt){
        return new ReceiptResource(
                receipt.getId(),
                receipt.getName(),
                receipt.getReceiptNumber(),
                receipt.getAmount(),
                receipt.getIssueDate(),
                receipt.getImagePath(),
                // Solo los comprobantes de gasto llevan la marca: son los unicos
                // sobre los que se corre la deteccion de duplicados.
                receipt instanceof ExpenseReceipt expenseReceipt
                        ? expenseReceipt.getSuspectedDuplicateOfReceiptId()
                        : null
        );
    }

    public static ReceiptOcrResource toResourceFromEntity(OcrReceipt receipt) {
        return toResourceFromEntity(OcrReceiptPreview.of(receipt, DuplicateReceiptCheck.none()));
    }

    public static ReceiptOcrResource toResourceFromEntity(OcrReceiptPreview preview) {
        OcrReceipt receipt = preview.receipt();
        DuplicateReceiptCheck duplicate = preview.duplicate();
        return new ReceiptOcrResource(
                receipt.getId(),
                receipt.getName(),
                receipt.getReceiptNumber(),
                receipt.getIssuerRuc(),
                receipt.getAmount(),
                receipt.getIssueDate(),
                receipt.getImagePath(),
                receipt.getOcrData().dataFields(),
                duplicate.isDuplicate(),
                duplicate.message(),
                duplicate.conflictingExpenseId(),
                duplicate.conflictingReceiptId()
        );
    }
}
