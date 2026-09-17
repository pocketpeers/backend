package com.pocketpeers.backend.operations.domain.model.valueobjects;

/**
 * Lo minimo para comparar huellas perceptuales sin traer el comprobante entero.
 *
 * <p>La similitud perceptual se mide por distancia de Hamming, no por igualdad,
 * asi que ningun indice de base de datos la resuelve: hay que recorrer los
 * candidatos. Proyectar tres columnas en vez de hidratar entidades es lo que
 * mantiene ese recorrido barato.</p>
 */
public record ReceiptPerceptualHash(Long receiptId, Long expenseId, Long perceptualHash) {
}
