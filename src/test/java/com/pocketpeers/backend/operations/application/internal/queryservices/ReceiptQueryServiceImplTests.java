package com.pocketpeers.backend.operations.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.pocketpeers.backend.operations.domain.exceptions.PaymentNotFoundException;
import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllReceiptsByExpenseIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetExpenseByIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetReceiptByIdQuery;
import com.pocketpeers.backend.operations.domain.services.ExpenseQueryService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptQueryServiceImplTests {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ExpenseQueryService expenseQueryService;

    @InjectMocks
    private ReceiptQueryServiceImpl service;

    @Test
    void getReceiptByIdDelegatesToRepository() {
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", BigDecimal.TEN, LocalDate.now(), "receipt.png", new Expense());
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        assertThat(service.handle(new GetReceiptByIdQuery(1L))).contains(receipt);
    }

    @Test
    void getReceiptsByExpenseReturnsExpenseReceipts() {
        Expense expense = new Expense("Cena", new BigDecimal("50.00"), null, null, LocalDate.now().plusDays(3));
        ExpenseReceipt receipt = new ExpenseReceipt("Boleta", BigDecimal.TEN, LocalDate.now(), "receipt.png", expense);
        expense.addReceipt(receipt);
        when(expenseQueryService.handle(new GetExpenseByIdQuery(7L))).thenReturn(Optional.of(expense));

        assertThat(service.handle(new GetAllReceiptsByExpenseIdQuery(7L))).containsExactly(receipt);
    }

    @Test
    void getReceiptsByExpenseThrowsWhenExpenseDoesNotExist() {
        when(expenseQueryService.handle(new GetExpenseByIdQuery(7L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new GetAllReceiptsByExpenseIdQuery(7L)))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
