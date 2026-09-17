package com.pocketpeers.backend.operations.domain.exceptions;

import lombok.Getter;

/**
 * El comprobante que se intenta registrar ya respalda otro gasto.
 *
 * <p>Lleva el gasto en conflicto porque el mensaje tiene que ser accionable: a
 * quien sube una boleta de buena fe hay que poder decirle exactamente donde ya
 * esta registrada, y a quien la reusa a proposito, que el sistema lo vio.</p>
 */
@Getter
public class DuplicateReceiptException extends RuntimeException {

    /**
     * Que senal lo detecto. Solo hay dos, y las dos son igualdades exactas.
     *
     * <p>La similitud perceptual quedo deliberadamente fuera: se solapa con la
     * distancia entre documentos distintos, asi que no puede sostener un
     * rechazo. Esa senal se anota en el comprobante y se revisa, no se lanza
     * desde aqui.</p>
     */
    public enum Signal {
        IMAGE_EXACT("la misma imagen"),
        DOCUMENT_NUMBER("el mismo RUC y numero de comprobante");

        private final String description;

        Signal(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final Signal signal;
    private final Long conflictingReceiptId;
    private final Long conflictingExpenseId;

    public DuplicateReceiptException(Signal signal, Long conflictingReceiptId, Long conflictingExpenseId) {
        super("Este comprobante ya fue registrado: se detecto " + signal.description()
                + " en el gasto " + conflictingExpenseId
                + " (comprobante " + conflictingReceiptId + ").");
        this.signal = signal;
        this.conflictingReceiptId = conflictingReceiptId;
        this.conflictingExpenseId = conflictingExpenseId;
    }
}
