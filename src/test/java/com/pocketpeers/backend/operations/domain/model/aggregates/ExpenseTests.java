package com.pocketpeers.backend.operations.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ExpenseTests {

    @Test
    void startsActivePendingAndFullyUnpaid() {
        Expense expense = new Expense("Alquiler", new BigDecimal("300.00"), null, null, LocalDate.now().plusDays(5));

        assertThat(expense.isActive()).isTrue();
        assertThat(expense.getStatus()).isEqualTo("pending");
        assertThat(expense.getTotalPaidAmount()).isEqualByComparingTo("0");
        assertThat(expense.getRemainingAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void onlyConfirmedPaymentsCountAsPaid() {
        Expense expense = new Expense("Alquiler", new BigDecimal("300.00"), null, null, LocalDate.now().plusDays(5));
        Payment unconfirmedPayment = new Payment("Parte 1", new BigDecimal("100.00"), null, expense);
        Payment confirmedPayment = new Payment("Parte 2", new BigDecimal("200.00"), null, expense);
        unconfirmedPayment.pay(new BigDecimal("100.00"));
        confirmedPayment.pay(new BigDecimal("200.00"));
        confirmedPayment.confirmPayment();

        expense.getPayments().add(unconfirmedPayment);
        expense.getPayments().add(confirmedPayment);

        assertThat(expense.getTotalPaidAmount()).isEqualByComparingTo("200.00");
        assertThat(expense.getRemainingAmount()).isEqualByComparingTo("100.00");
        assertThat(expense.getStatus()).isEqualTo("pending");
    }

    @Test
    void completedWhenConfirmedPaymentsMatchTotalAmount() {
        Expense expense = new Expense("Alquiler", new BigDecimal("300.00"), null, null, LocalDate.now().plusDays(5));
        Payment payment = new Payment("Pago completo", new BigDecimal("300.00"), null, expense);
        payment.pay(new BigDecimal("300.00"));
        payment.confirmPayment();

        expense.getPayments().add(payment);

        assertThat(expense.getStatus()).isEqualTo("completed");
        assertThat(expense.getRemainingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cancelledExpenseCannotBeUpdated() {
        Expense expense = new Expense("Alquiler", new BigDecimal("300.00"), null, null, LocalDate.now().plusDays(5));
        expense.cancel();

        assertThat(expense.isActive()).isFalse();
        assertThat(expense.getStatus()).isEqualTo("cancelled");
        assertThatThrownBy(() -> expense.UpdateInformation("Nuevo", new BigDecimal("350.00"), LocalDate.now().plusDays(6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cancelled expenses cannot be updated");
    }
}
