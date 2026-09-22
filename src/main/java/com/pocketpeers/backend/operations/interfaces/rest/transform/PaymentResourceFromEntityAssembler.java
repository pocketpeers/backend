package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentResource;

import java.util.Date;

public class PaymentResourceFromEntityAssembler {
    public static PaymentResource toResourceFromEntity(Payment payment) {
        return toResourceFromEntity(payment, false);
    }

    public static PaymentResource toResourceFromEntity(Payment payment, boolean includeEvidence) {
        return toResourceFromEntity(payment, includeEvidence, "");
    }

    public static PaymentResource toResourceFromEntity(Payment payment, boolean includeEvidence, String blockchainHash) {
        return toResourceFromEntity(payment, includeEvidence, blockchainHash, null);
    }

    /**
     * @param anchoredAt instante en que la operacion quedo escrita en la cadena.
     *                   Nulo mientras no lo este, que es el mismo caso en el que
     *                   {@code blockchainHash} viene vacio: los dos salen del
     *                   mismo eslabon, asi que o llegan juntos o no llega
     *                   ninguno.
     */
    public static PaymentResource toResourceFromEntity(Payment payment,
                                                       boolean includeEvidence,
                                                       String blockchainHash,
                                                       Date anchoredAt) {
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
                anchoredAt,
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getPaidAt(),
                includeEvidence
                        ? payment.getEvidences().stream()
                                .map(evidence -> evidence.getPhoto())
                                .filter(photo -> photo != null && !photo.isBlank())
                                .toList()
                        : java.util.List.of()
        );
    }
}
