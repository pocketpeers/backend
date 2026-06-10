package com.pocketpeers.backend.operations.application.internal.queryservices;

import com.pocketpeers.backend.operations.domain.exceptions.PaymentNotFoundException;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import com.pocketpeers.backend.operations.domain.model.queries.*;
import com.pocketpeers.backend.operations.domain.services.ExpenseQueryService;
import com.pocketpeers.backend.operations.domain.services.ReceiptQueryService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ReceiptQueryServiceImpl implements ReceiptQueryService {
    private ReceiptRepository receiptRepository;
    private ExpenseQueryService expenseQueryService;

    @Override
    public Optional<Receipt> handle(GetReceiptByIdQuery query) {
        return receiptRepository.findById(query.receiptId());
    }

    @Override
    public List<ExpenseReceipt> handle(GetAllReceiptsByExpenseIdQuery query) {
        var queryExpense = new GetExpenseByIdQuery(query.expenseId());
        var expense = expenseQueryService.handle(queryExpense)
                .orElseThrow(() -> new PaymentNotFoundException(query.expenseId()));

        return expense.getReceipts();
    }

}
