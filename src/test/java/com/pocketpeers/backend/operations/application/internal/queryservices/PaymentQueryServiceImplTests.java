package com.pocketpeers.backend.operations.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllPaymentsByExpenseIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllPaymentsByUserIdAndStatusQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllPaymentsByUserIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllPaymentsQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetIncomingPaymentsByUserIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetPaymentByIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetPaymentByUserIdAndExpenseId;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceImplTests {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentQueryServiceImpl service;

    @Test
    void paymentListsFilterOutCancelledOrMissingExpensePayments() {
        Payment active = paymentWithExpense(expense());
        Payment cancelled = paymentWithExpense(expense().cancel());
        Payment withoutExpense = new Payment("Sin gasto", BigDecimal.TEN, null, null);
        when(paymentRepository.findAll()).thenReturn(List.of(active, cancelled, withoutExpense));
        when(paymentRepository.findAllByUser_Id(1L)).thenReturn(List.of(active, cancelled, withoutExpense));
        when(paymentRepository.findAllByExpenseId(2L)).thenReturn(List.of(active, cancelled, withoutExpense));
        when(paymentRepository.findAllByUser_IdAndStatus(1L, PaymentStatus.PENDING)).thenReturn(List.of(active, cancelled, withoutExpense));

        assertThat(service.handle(new GetAllPaymentsQuery())).containsExactly(active);
        assertThat(service.handle(new GetAllPaymentsByUserIdQuery(1L))).containsExactly(active);
        assertThat(service.handle(new GetAllPaymentsByExpenseIdQuery(2L))).containsExactly(active);
        assertThat(service.handle(new GetAllPaymentsByUserIdAndStatusQuery(1L, PaymentStatus.PENDING))).containsExactly(active);
    }

    @Test
    void getByIdAndByUserExpenseDelegateToRepository() {
        Payment payment = paymentWithExpense(expense());
        when(paymentRepository.findById(9L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByUser_IdAndExpenseId(1L, 2L)).thenReturn(Optional.of(payment));

        assertThat(service.handle(new GetPaymentByIdQuery(9L))).contains(payment);
        assertThat(service.handle(new GetPaymentByUserIdAndExpenseId(1L, 2L))).contains(payment);
    }

    @Test
    void incomingPaymentsRequireExistingUserAndFilterActiveExpenses() {
        User user = new User("admin", "secret");
        ReflectionTestUtils.setField(user, "id", 1L);
        Payment active = paymentWithExpense(expense());
        Payment cancelled = paymentWithExpense(expense().cancel());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paymentRepository.findIncomingPaymentsByUser(1L, GroupRole.ADMIN)).thenReturn(List.of(active, cancelled));

        assertThat(service.handle(new GetIncomingPaymentsByUserIdQuery(1L))).containsExactly(active);
    }

    @Test
    void incomingPaymentsThrowWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new GetIncomingPaymentsByUserIdQuery(99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User information not found for ID: 99");
    }

    private Payment paymentWithExpense(Expense expense) {
        return new Payment("Pago", new BigDecimal("10.00"), null, expense);
    }

    private Expense expense() {
        return new Expense("Cena", new BigDecimal("50.00"), null, null, LocalDate.now().plusDays(3));
    }
}
