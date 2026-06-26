package com.pocketpeers.backend.operations.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.OcrData;
import org.junit.jupiter.api.Test;

class ReceiptTests {

    @Test
    void expenseReceiptStoresReceiptFieldsAndCanBeAssignedToExpense() {
        Expense expense = new Expense();
        Expense otherExpense = new Expense();
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", "R-001", new BigDecimal("20.50"), LocalDate.now(), "receipt.png", expense);

        receipt.assignToExpense(otherExpense);

        assertThat(receipt.getName()).isEqualTo("Boleta");
        assertThat(receipt.getReceiptNumber()).isEqualTo("R-001");
        assertThat(receipt.getAmount()).isEqualByComparingTo("20.50");
        assertThat(receipt.getImagePath()).isEqualTo("receipt.png");
        assertThat(receipt.getIsActive()).isTrue();
    }

    @Test
    void ocrReceiptStoresOcrDataAndOriginalReceipt() {
        OcrData data = new OcrData(Map.of("merchant", "Tienda"));
        OcrReceipt ocrReceipt = new OcrReceipt("Boleta OCR", "R-002", new BigDecimal("30.00"), LocalDate.now(), "ocr.png", data);
        ExpenseReceipt original = new ExpenseReceipt("Boleta", new BigDecimal("30.00"), LocalDate.now(), "receipt.png", new Expense());

        ocrReceipt.assignToOriginalReceipt(original);

        assertThat(ocrReceipt.getOcrData().dataFields()).containsEntry("merchant", "Tienda");
        assertThat(ocrReceipt.getOriginalReceipt()).isSameAs(original);
    }
}
