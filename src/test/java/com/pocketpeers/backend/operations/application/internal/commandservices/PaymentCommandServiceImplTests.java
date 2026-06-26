package com.pocketpeers.backend.operations.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.commands.ConfirmPaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.MakePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentEvidence;
import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentEvidenceRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceImplTests {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PaymentEvidenceRepository paymentEvidenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private PblCommandService pblCommandService;

    @Mock
    private ReputationEventRepository reputationEventRepository;

    @InjectMocks
    private PaymentCommandServiceImpl service;

    @Test
    void createPaymentSavesPaymentAndPublishesEvent() {
        User payer = user("ana", 11L);
        Expense expense = expense(user("owner", 1L), group(2L), LocalDate.now().plusDays(5));
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(userRepository.findById(11L)).thenReturn(Optional.of(payer));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 99L);
            return payment;
        });

        Long paymentId = service.handle(new CreatePaymentCommand("Cuota", new BigDecimal("45.00"), 11L, 3L));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        verify(applicationEventPublisher).publishEvent(any(PaymentCreatedEvent.class));
        assertThat(paymentId).isEqualTo(99L);
        assertThat(paymentCaptor.getValue().getUser()).isSameAs(payer);
        assertThat(paymentCaptor.getValue().getExpense()).isSameAs(expense);
    }

    @Test
    void createPaymentRejectsCancelledExpense() {
        Expense expense = expense(user("owner", 1L), group(2L), LocalDate.now().plusDays(5));
        expense.cancel();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> service.handle(new CreatePaymentCommand("Cuota", new BigDecimal("45.00"), 11L, 3L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cancelled expenses cannot receive payments");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void makePaymentRegistersAmountEvidenceAndEvent() {
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().plusDays(5));
        ReflectionTestUtils.setField(payment, "id", 42L);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        Long paymentId = service.handle(new MakePaymentCommand(42L, new BigDecimal("30.00"), "evidence.png"));

        ArgumentCaptor<PaymentEvidence> evidenceCaptor = ArgumentCaptor.forClass(PaymentEvidence.class);
        verify(paymentEvidenceRepository).save(evidenceCaptor.capture());
        verify(paymentRepository).save(payment);
        verify(applicationEventPublisher).publishEvent(any(PaymentUpdatedEvent.class));
        assertThat(paymentId).isEqualTo(42L);
        assertThat(payment.getAmountPaid()).isEqualByComparingTo("30.00");
        assertThat(payment.getStatus()).isEqualTo("PARTIAL");
        assertThat(evidenceCaptor.getValue().getPayment()).isSameAs(payment);
    }

    @Test
    void makePaymentThrowsWhenPaymentDoesNotExist() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new MakePaymentCommand(42L, new BigDecimal("30.00"), null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Payment not found");
    }

    @Test
    void confirmPaymentRequiresExpenseCreator() {
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().plusDays(5));
        payment.pay(new BigDecimal("80.00"));
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        ReflectionTestUtils.setField(payment, "id", 42L);

        assertThatThrownBy(() -> service.handle(new ConfirmPaymentCommand(42L, "otro")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Only the creator of the expense can confirm the payment");
    }

    @Test
    void confirmPaymentConfirmsAndRegistersReputation() {
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().plusDays(5));
        ReflectionTestUtils.setField(payment, "id", 42L);
        payment.pay(new BigDecimal("80.00"));
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(any(), any())).thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        Long paymentId = service.handle(new ConfirmPaymentCommand(42L, "owner"));

        ArgumentCaptor<RegisterReputationEventCommand> reputationCaptor =
                ArgumentCaptor.forClass(RegisterReputationEventCommand.class);
        verify(paymentRepository).save(payment);
        verify(pblCommandService, atLeastOnce()).handle(reputationCaptor.capture());
        verify(applicationEventPublisher).publishEvent(any(PaymentUpdatedEvent.class));
        assertThat(paymentId).isEqualTo(42L);
        assertThat(payment.getConfirmed()).isTrue();
        assertThat(reputationCaptor.getAllValues())
                .extracting(RegisterReputationEventCommand::type)
                .contains(ReputationEventType.ON_TIME_PAYMENT);
    }

    private Payment paymentWithExpense(BigDecimal amount, LocalDate dueDate) {
        User payer = user("ana", 11L);
        Expense expense = expense(user("owner", 1L), group(2L), dueDate);
        return new Payment("Cuota", amount, payer, expense);
    }

    private Expense expense(User owner, Group group, LocalDate dueDate) {
        return new Expense("Gasto", new BigDecimal("100.00"), owner, group, dueDate);
    }

    private Group group(Long id) {
        Group group = new Group("Casa", "Gastos", "photo.png");
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private User user(String username, Long id) {
        User user = new User(username, "secret");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
