package com.pocketpeers.backend.operations.domain.model.events;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;

public record PaymentUpdatedEvent(
        Payment payment
) {
}
