package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface PaymentQueryService {
    List<Payment> handle(GetAllPaymentsQuery query);
    Optional<Payment> handle(GetPaymentByIdQuery query);
    List<Payment> handle(GetAllPaymentsByUserInformationIdQuery query);
    List<Payment> handle(GetAllPaymentsByExpenseIdQuery query);
    Optional<Payment> handle(GetPaymentByUserInformationIdAndExpenseId query);
    List<Payment> handle(GetAllPaymentsByUserIdAndStatusQuery query);

    List<Payment> handle(GetIncomingPaymentsByUserInformationIdQuery query);
}
