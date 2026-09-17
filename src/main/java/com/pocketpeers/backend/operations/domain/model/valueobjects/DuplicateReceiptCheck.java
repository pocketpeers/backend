package com.pocketpeers.backend.operations.domain.model.valueobjects;

import com.pocketpeers.backend.operations.domain.exceptions.DuplicateReceiptException;

/**
 * Veredicto del control de duplicados, sin lanzar nada.
 *
 * <p>Existe porque la misma comprobacion hace falta en dos momentos distintos y
 * solo en uno de ellos corresponde abortar. Al registrar el comprobante el
 * duplicado es un error y va como excepcion; al leer la imagen con OCR todavia
 * no hay nada que abortar —el gasto ni siquiera existe— y lo que se necesita es
 * poder <i>avisar</i> antes de que la persona siga adelante.</p>
 *
 * <p>Compartir el veredicto en vez de duplicar las consultas es lo que garantiza
 * que el aviso del OCR y el rechazo del registro digan siempre lo mismo. Si
 * fueran dos implementaciones, bastaria tocar una para que la app dejara pasar
 * algo que el backend luego rechaza, que es exactamente el fallo que este tipo
 * viene a cerrar.</p>
 */
public record DuplicateReceiptCheck(
        DuplicateReceiptException.Signal signal,
        Long conflictingReceiptId,
        Long conflictingExpenseId
) {

    private static final DuplicateReceiptCheck NONE = new DuplicateReceiptCheck(null, null, null);

    /** Sin conflicto: el comprobante no se parece a ninguno ya registrado. */
    public static DuplicateReceiptCheck none() {
        return NONE;
    }

    public boolean isDuplicate() {
        return signal != null;
    }

    /**
     * Mensaje listo para mostrar, con el mismo texto que el rechazo del registro.
     *
     * <p>Se arma aqui y no en el cliente para que las dos rutas no se separen: el
     * aviso previo y el error posterior describen el mismo hecho, y si el texto
     * viviera en la app habria que mantenerlo en dos lugares.</p>
     */
    public String message() {
        if (!isDuplicate()) {
            return null;
        }
        return "Este comprobante ya fue registrado: se detecto " + signal.description()
                + " en el gasto " + conflictingExpenseId
                + " (comprobante " + conflictingReceiptId + ").";
    }

    public DuplicateReceiptException toException() {
        return new DuplicateReceiptException(signal, conflictingReceiptId, conflictingExpenseId);
    }
}
