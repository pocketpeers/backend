package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.commands.CompletePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;

public interface PaymentCommandService {
    Long handle(CreatePaymentCommand command);
    Long handle(CompletePaymentCommand command);
}
