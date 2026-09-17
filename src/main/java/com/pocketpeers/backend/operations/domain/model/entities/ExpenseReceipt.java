package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comprobante adjunto a un gasto.
 *
 * <p>Las senales antiduplicado viven aqui y no en {@link Receipt} a proposito.
 * La jerarquia es {@code JOINED}, asi que {@code OcrReceipt} tambien escribe en
 * la tabla base, y un {@code OcrReceipt} se deriva <i>de la misma imagen</i> que
 * su original: un indice unico sobre la tabla base haria que cada lectura OCR
 * chocara con el comprobante que la origino. Aqui abajo solo hay afirmaciones de
 * gasto, que es donde repetir una imagen significa cobrar dos veces.</p>
 */
@Entity
@PrimaryKeyJoinColumn(name = "receipt_id")
public class ExpenseReceipt extends Receipt {
    @Getter
    @ManyToOne()
    @JoinColumn(name = "expense_id")
    private Expense expense;

    /**
     * RUC del emisor. Junto con la serie-numero forma la llave logica del
     * documento: la serie-numero sola no sirve, porque solo es unica por
     * emisor y dos negocios distintos pueden emitir ambos B001-0001234.
     */
    @Getter
    @Column(length = 11)
    private String issuerRuc;

    /**
     * Serie-numero en forma canonica, duplicado aqui desde {@code Receipt}.
     *
     * <p>Es redundante a proposito. La llave logica es RUC + serie-numero, pero
     * el RUC vive en esta tabla y el numero en la base, y PostgreSQL no admite
     * un indice unico que abarque dos tablas. Materializar el numero aqui es lo
     * que permite imponer el par completo sin mover la restriccion a la tabla
     * base, donde chocaria con los comprobantes OCR.</p>
     *
     * <p>Se guarda en mayusculas porque el OCR alterna entre "B001-123" y
     * "b001-123" sobre la misma boleta: sin normalizar, dos lecturas del mismo
     * documento serian dos llaves distintas y el duplicado pasaria.</p>
     */
    @Getter
    @Column(length = 64)
    private String documentNumber;

    /** Huella de los bytes de la imagen, copiada de {@code Image} al registrar. */
    @Getter
    @Column(length = 64)
    private String imageSha256;

    /** dHash de la imagen. Senal de sospecha, nunca motivo de rechazo. */
    @Getter
    private Long imagePerceptualHash;

    /**
     * Comprobante al que este se parece visualmente, si alguno.
     *
     * <p>Se llena cuando la distancia perceptual cae bajo el umbral. No impide
     * registrar: la medicion mostro que las distancias entre documentos
     * distintos y entre reenvios de uno mismo se solapan, asi que rechazar por
     * esta senal expulsaria usuarios honestos. Queda anotado para que una
     * persona lo revise.</p>
     */
    @Getter
    private Long suspectedDuplicateOfReceiptId;

    public ExpenseReceipt() {}

    public ExpenseReceipt(String name, BigDecimal amount, LocalDate issueDate, String imagePath, Expense expense) {
        super(name, amount, issueDate, imagePath);
        this.expense = expense;
    }

    public ExpenseReceipt(String name, String receiptNumber, BigDecimal amount, LocalDate issueDate, String imagePath, Expense expense) {
        super(name, amount, issueDate, imagePath, receiptNumber);
        this.expense = expense;
    }

    public void assignToExpense(Expense expense) {
        this.expense = expense;
    }

    /**
     * Sella las senales con que se detectaran futuros duplicados.
     *
     * <p>Los vacios se normalizan a null porque las columnas llevan indice unico
     * parcial: en SQL dos NULL no colisionan, pero dos cadenas vacias si, y eso
     * haria que el segundo comprobante sin datos fuera rechazado como duplicado
     * del primero.</p>
     */
    public void sealFingerprint(String issuerRuc, String imageSha256, Long imagePerceptualHash) {
        this.issuerRuc = blankToNull(issuerRuc);
        this.imageSha256 = blankToNull(imageSha256);
        this.imagePerceptualHash = imagePerceptualHash;
        this.documentNumber = canonicalDocumentNumber(getReceiptNumber());
    }

    /** Forma canonica con que se compara un numero de comprobante. */
    public static String canonicalDocumentNumber(String receiptNumber) {
        String trimmed = blankToNull(receiptNumber);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    /** Anota que este comprobante se parece a otro ya registrado. */
    public void flagSuspectedDuplicateOf(Long receiptId) {
        this.suspectedDuplicateOfReceiptId = receiptId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
