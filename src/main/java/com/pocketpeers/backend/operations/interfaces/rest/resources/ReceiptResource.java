package com.pocketpeers.backend.operations.interfaces.rest.resources;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param suspectedDuplicateOfReceiptId comprobante al que este se parece
 *        visualmente, o null. Viaja para que la interfaz pueda avisar "esta
 *        boleta se parece a una ya registrada" y ofrecer verla. No es un
 *        rechazo: el registro ya ocurrio.
 */
public record ReceiptResource(
        Long id,
        String name,
        String receiptNumber,
        BigDecimal amount,
        LocalDate issueDate,
        String imagePath,
        Long suspectedDuplicateOfReceiptId
) {
}
