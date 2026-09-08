package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.commands.ConfirmPaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.MakePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;

public interface PaymentCommandService {
    Long handle(CreatePaymentCommand command);
    Long handle(MakePaymentCommand command);
    Long handle(ConfirmPaymentCommand command);

    /**
     * Registra el castigo de reputacion de todo pago cuyo plazo ya cerro.
     *
     * <p>Lo invoca el planificador. Vive en este servicio porque los hechos que
     * el evento necesita salen del pago y del gasto, no del reloj.</p>
     *
     * @return cuantos pagos vencidos se examinaron
     */
    int registerOverduePenalties();
}
