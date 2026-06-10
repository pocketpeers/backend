package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;

public record MakePaymentCommand(Long paymentId, BigDecimal amount, String photo) {
}
