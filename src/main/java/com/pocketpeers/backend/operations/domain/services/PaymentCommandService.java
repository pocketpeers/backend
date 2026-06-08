package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.commands.ConfirmPaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.MakePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;

public interface PaymentCommandService {
    Long handle(CreatePaymentCommand command);
    Long handle(MakePaymentCommand command);
    Long handle(ConfirmPaymentCommand command);
}
