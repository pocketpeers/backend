package com.pocketpeers.backend.operations.domain.model.commands;

public record ConfirmPaymentCommand(Long paymentId, String username) {
}
