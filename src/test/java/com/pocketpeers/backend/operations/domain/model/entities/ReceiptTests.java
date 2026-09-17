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
        OcrReceipt ocrReceipt = new OcrReceipt("Boleta OCR", "R-002", "20131312955", new BigDecimal("30.00"), LocalDate.now(), "ocr.png", data);
        ExpenseReceipt original = new ExpenseReceipt("Boleta", new BigDecimal("30.00"), LocalDate.now(), "receipt.png", new Expense());

        ocrReceipt.assignToOriginalReceipt(original);

        assertThat(ocrReceipt.getOcrData().dataFields()).containsEntry("merchant", "Tienda");
        assertThat(ocrReceipt.getIssuerRuc()).isEqualTo("20131312955");
        assertThat(ocrReceipt.getOriginalReceipt()).isSameAs(original);
    }

    /**
     * El indice unico es parcial sobre los nulos. Si una cadena vacia llegara a
     * guardarse tal cual, todos los comprobantes cuyo OCR no leyo nada
     * colisionarian entre si y el segundo usuario con una foto borrosa quedaria
     * bloqueado por el primero.
     */
    @Test
    void sealFingerprintNormalizaVaciosANulo() {
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", new BigDecimal("10.00"), LocalDate.now(), "img", new Expense());

        receipt.sealFingerprint("   ", "", null);

        assertThat(receipt.getIssuerRuc()).isNull();
        assertThat(receipt.getImageSha256()).isNull();
        assertThat(receipt.getImagePerceptualHash()).isNull();
    }

    /**
     * La llave logica se compara en mayusculas. Si no, dos lecturas OCR de la
     * misma boleta ("B001-123" y "b001-123") serian dos documentos distintos y
     * el duplicado pasaria sin que nadie lo note.
     */
    @Test
    void sealFingerprintCanonizaElNumeroDeDocumento() {
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", " b001-123 ", new BigDecimal("10.00"), LocalDate.now(), "img", new Expense());

        receipt.sealFingerprint("20131312955", "abc", 1L);

        assertThat(receipt.getDocumentNumber()).isEqualTo("B001-123");
    }

    @Test
    void sealFingerprintDejaNuloElDocumentoSinNumero() {
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", new BigDecimal("10.00"), LocalDate.now(), "img", new Expense());

        receipt.sealFingerprint("20131312955", "abc", 1L);

        assertThat(receipt.getDocumentNumber()).isNull();
    }

    @Test
    void sealFingerprintConservaLosValoresUtiles() {
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", new BigDecimal("10.00"), LocalDate.now(), "img", new Expense());

        receipt.sealFingerprint(" 20131312955 ", " abc123 ", 42L);

        assertThat(receipt.getIssuerRuc()).isEqualTo("20131312955");
        assertThat(receipt.getImageSha256()).isEqualTo("abc123");
        assertThat(receipt.getImagePerceptualHash()).isEqualTo(42L);
    }
}
