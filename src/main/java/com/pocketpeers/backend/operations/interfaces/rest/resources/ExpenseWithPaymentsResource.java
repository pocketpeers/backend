package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.util.List;

public record ExpenseWithPaymentsResource(
        ExpenseResource expense,
        List<PaymentResource> payments
) {
}
