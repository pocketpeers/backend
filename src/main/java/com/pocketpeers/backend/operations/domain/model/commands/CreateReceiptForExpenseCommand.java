package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param issuerRuc RUC leido del comprobante por el OCR. Puede llegar nulo
 *                  —hay comprobantes sin RUC legible— y entonces la llave
 *                  logica no aplica y el control queda en manos de la huella
 *                  de imagen, que el servidor deriva por su cuenta.
 */
public record CreateReceiptForExpenseCommand(
        String name,
        String receiptNumber,
        String issuerRuc,
        BigDecimal amount,
        LocalDate issueDate,
        String imagePath,
        Long expenseId
) {
}
