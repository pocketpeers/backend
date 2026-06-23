package com.pocketpeers.backend.operations.domain.model.queries;

import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;

public record GetAllPaymentsByUserIdAndStatusQuery(Long userId, PaymentStatus status) {
}
