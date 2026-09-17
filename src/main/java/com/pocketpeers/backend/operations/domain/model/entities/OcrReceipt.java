package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.valueobjects.OcrData;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class OcrReceipt extends Receipt{
    @Embedded
    private OcrData ocrData;

    /**
     * RUC leido de la imagen.
     *
     * <p>Vive en esta tabla y no en {@code Receipt} a proposito: la jerarquia es
     * {@code JOINED} y un {@code OcrReceipt} se deriva de la misma imagen que su
     * original, asi que tener la llave logica en la tabla base haria que la
     * lectura OCR chocara con el comprobante que la produjo. Aqui es solo dato;
     * la unicidad se impone sobre {@code ExpenseReceipt}.</p>
     */
    @Column(length = 11)
    private String issuerRuc;

    @OneToOne
    @JoinColumn(name = "receipt_id")
    private Receipt originalReceipt;

    public OcrReceipt() {}
    public OcrReceipt(
            String name,
            String receiptNumber,
            String issuerRuc,
            BigDecimal amount,
            LocalDate issueDate,
            String imagePath,
            OcrData ocrData
    ) {
        super(name, amount, issueDate, imagePath,receiptNumber);
        this.issuerRuc = issuerRuc;
        this.ocrData = ocrData;
    }


    public void assignToOriginalReceipt(Receipt originalReceipt) {
        this.originalReceipt = originalReceipt;
    }
}
