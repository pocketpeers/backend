package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentResource;

public class PaymentResourceFromEntityAssembler {
    public static PaymentResource toResourceFromEntity(Payment payment) {
        return new PaymentResource(payment.getId(), payment.getDescription(), payment.getAmount(), payment.getStatus(), payment.getUser().getId(), payment.getExpense().getId());
    }
}
