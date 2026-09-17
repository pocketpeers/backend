package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Lo leido de una boleta, mas el aviso de que ya estaba registrada.
 *
 * <p>Los cuatro campos de duplicado van en esta misma respuesta y no en una
 * llamada aparte porque la app decide con ellos si deja continuar. La lectura
 * del OCR es el ultimo momento en que puede hacerlo sin dejar rastro: el
 * siguiente paso crea el gasto y reparte los pagos entre el grupo.</p>
 *
 * @param duplicate            si esta boleta ya respalda otro gasto
 * @param duplicateMessage     texto listo para mostrar, o null si no hay conflicto
 * @param duplicateOfExpenseId gasto donde ya esta registrada
 * @param duplicateOfReceiptId comprobante concreto con el que choca
 */
public record ReceiptOcrResource(
        Long id,
        String name,
        String receiptNumber,
        String issuerRuc,
        BigDecimal amount,
        LocalDate issueDate,
        String imagePath,
        Map<String,Object> dataFields,
        boolean duplicate,
        String duplicateMessage,
        Long duplicateOfExpenseId,
        Long duplicateOfReceiptId
) {
}
