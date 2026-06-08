package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.commands.MakePaymentCommand;
import com.pocketpeers.backend.operations.interfaces.rest.resources.MakePaymentResource;

public class MakePaymentCommandFromResourceAssembler {
    public static MakePaymentCommand toCommandFromResource(MakePaymentResource resource, Long paymentId) {
        return new MakePaymentCommand(paymentId, resource.amount(), resource.photo());
    }
}
