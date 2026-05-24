package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllReceiptsByExpenseIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllReceiptsByPaymentIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetReceiptByIdQuery;

import java.util.List;
import java.util.Optional;

public interface ReceiptQueryService {
    List<PaymentReceipt> handle(GetAllReceiptsByPaymentIdQuery query);
    Optional<Receipt> handle(GetReceiptByIdQuery query);
    List<ExpenseReceipt> handle(GetAllReceiptsByExpenseIdQuery query);

}
