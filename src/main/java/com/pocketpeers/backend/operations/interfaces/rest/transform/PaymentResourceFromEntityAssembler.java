package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentResource;

public class PaymentResourceFromEntityAssembler {
    public static PaymentResource toResourceFromEntity(Payment payment) {
        return toResourceFromEntity(payment, false);
    }

    public static PaymentResource toResourceFromEntity(Payment payment, boolean includeEvidence) {
        return toResourceFromEntity(payment, includeEvidence, "");
    }

    public static PaymentResource toResourceFromEntity(Payment payment, boolean includeEvidence, String blockchainHash) {
        return new PaymentResource(
                payment.getId(),
                payment.getDescription(),
                payment.getAmount(),
                payment.getAmountPaid(),
                payment.getStatus(),
                payment.getConfirmed(),
                payment.getUser().getId(),
                payment.getExpense().getId(),
                blockchainHash,
                includeEvidence
                        ? payment.getEvidences().stream()
                                .map(evidence -> evidence.getPhoto())
                                .filter(photo -> photo != null && !photo.isBlank())
                                .toList()
                        : java.util.List.of()
        );
    }
}
