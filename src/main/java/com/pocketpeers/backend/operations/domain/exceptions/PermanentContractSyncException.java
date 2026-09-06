package com.pocketpeers.backend.operations.domain.exceptions;

/**
 * Falla de sincronizacion con la cadena que no se va a resolver reintentando.
 *
 * <p>Existe para separar dos situaciones que antes se trataban igual: que el
 * contrato del gasto todavia no este desplegado, que es transitorio y se resuelve
 * esperando, y que la cuenta ya exista en la cadena, que no se resuelve nunca.</p>
 *
 * <p>La distincion importa porque cada reintento firma y envia una transaccion
 * real. Reintentar treinta veces una condicion permanente multiplica por treinta
 * el costo en comisiones sin ninguna posibilidad de exito.</p>
 */
public class PermanentContractSyncException extends RuntimeException {

    public PermanentContractSyncException(String message) {
        super(message);
    }

    public PermanentContractSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
