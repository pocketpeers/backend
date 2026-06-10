package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;

public record MakePaymentResource(BigDecimal amount, String photo) {
}
