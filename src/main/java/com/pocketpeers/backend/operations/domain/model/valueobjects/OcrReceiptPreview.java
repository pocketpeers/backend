package com.pocketpeers.backend.operations.domain.model.valueobjects;

import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;

/**
 * Lo leido de una imagen, junto con el veredicto de duplicados.
 *
 * <p>Los dos datos viajan juntos porque la app los necesita en la misma
 * respuesta: con el OCR rellena el formulario y con el veredicto decide si
 * permite continuar. Pedirlos por separado abriria una ventana en la que la
 * pantalla ya se completo y todavia no sabe que la boleta esta repetida.</p>
 */
public record OcrReceiptPreview(OcrReceipt receipt, DuplicateReceiptCheck duplicate) {

    public static OcrReceiptPreview of(OcrReceipt receipt, DuplicateReceiptCheck duplicate) {
        return new OcrReceiptPreview(receipt, duplicate == null ? DuplicateReceiptCheck.none() : duplicate);
    }
}
