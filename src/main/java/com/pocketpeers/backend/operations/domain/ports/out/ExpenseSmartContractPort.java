package com.pocketpeers.backend.operations.domain.ports.out;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;

public interface ExpenseSmartContractPort {
    ContractAddress deployExpenseContract(Expense expense) throws Exception;

    /**
     * Deja constancia en cadena del estado actual de un pago.
     *
     * <p>Sustituye a las dos operaciones anteriores, {@code addPayment} y
     * {@code updatePaymentStatus}. Ya no son cosas distintas: el programa no
     * guarda estado por pago, asi que registrar un pago nuevo y corregir uno
     * existente son la misma operacion —añadir un eslabon a la cadena del
     * gasto—. El historial no se edita, se extiende.</p>
     */
    TransactionHash recordPayment(Expense expense, Payment payment) throws Exception;
}
