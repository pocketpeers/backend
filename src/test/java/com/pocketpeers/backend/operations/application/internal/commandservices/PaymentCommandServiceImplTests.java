package com.pocketpeers.backend.operations.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void confirmPaymentKeepsOnTimeCreditWhenTheCreatorConfirmsLate() {
        // The payer settled two days before the due date; the creator only got
        // around to confirming three days after it. The delay is not the payer's
        // behaviour, so it must not cost them the on time credit.
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().minusDays(3));
        ReflectionTestUtils.setField(payment, "id", 42L);
        payment.pay(new BigDecimal("80.00"));
        ReflectionTestUtils.setField(payment, "paidAt", LocalDateTime.now().minusDays(5));
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(any(), any())).thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        service.handle(new ConfirmPaymentCommand(42L, "owner"));

        ArgumentCaptor<RegisterReputationEventCommand> reputationCaptor =
                ArgumentCaptor.forClass(RegisterReputationEventCommand.class);
        verify(pblCommandService, atLeastOnce()).handle(reputationCaptor.capture());
        assertThat(reputationCaptor.getAllValues())
                .extracting(RegisterReputationEventCommand::type)
                .contains(ReputationEventType.ON_TIME_PAYMENT)
                .doesNotContain(ReputationEventType.OVERDUE_PAYMENT, ReputationEventType.LATE_PAYMENT);
    }

    @Test
    void confirmPaymentPenalisesPaymentActuallyMadeAfterTheDueDate() {
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().minusDays(3));
        ReflectionTestUtils.setField(payment, "id", 42L);
        payment.pay(new BigDecimal("80.00"));
        ReflectionTestUtils.setField(payment, "paidAt", LocalDateTime.now().minusDays(1));
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(any(), any())).thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        service.handle(new ConfirmPaymentCommand(42L, "owner"));

        ArgumentCaptor<RegisterReputationEventCommand> reputationCaptor =
                ArgumentCaptor.forClass(RegisterReputationEventCommand.class);
        verify(pblCommandService, atLeastOnce()).handle(reputationCaptor.capture());
        assertThat(reputationCaptor.getAllValues())
                .extracting(RegisterReputationEventCommand::type)
                .contains(ReputationEventType.OVERDUE_PAYMENT, ReputationEventType.LATE_PAYMENT)
                .doesNotContain(ReputationEventType.ON_TIME_PAYMENT);
    }

    @Test
    void confirmPaymentFallsBackToConfirmationTimeWhenThePaymentMarkIsMissing() {
        // Payments registered before `paidAt` existed carry no mark, and for
        // those the old behaviour of judging them at confirmation time is all
        // there is to go on.
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().minusDays(2));
        ReflectionTestUtils.setField(payment, "id", 42L);
        payment.pay(new BigDecimal("80.00"));
        ReflectionTestUtils.setField(payment, "paidAt", null);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(any(), any())).thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        service.handle(new ConfirmPaymentCommand(42L, "owner"));

        ArgumentCaptor<RegisterReputationEventCommand> reputationCaptor =
                ArgumentCaptor.forClass(RegisterReputationEventCommand.class);
        verify(pblCommandService, atLeastOnce()).handle(reputationCaptor.capture());
        assertThat(reputationCaptor.getAllValues())
                .extracting(RegisterReputationEventCommand::type)
                .contains(ReputationEventType.OVERDUE_PAYMENT, ReputationEventType.LATE_PAYMENT);
    }

    @Test
    void confirmedPaymentCarriesTheFactsPeerScoreNeeds() {
        // Sin acreedor y monto el evento solo sirve al contador de puntos: el
        // score nuevo no tendria con quien contrastar la conducta ni como pesarla.
        LocalDate dueDate = LocalDate.now().plusDays(5);
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), dueDate);
        ReflectionTestUtils.setField(payment, "id", 42L);
        payment.pay(new BigDecimal("80.00"));
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(any(), any())).thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        service.handle(new ConfirmPaymentCommand(42L, "owner"));

        var core = capturedCommandOfType(ReputationEventType.ON_TIME_PAYMENT);
        assertThat(core.counterpartyId()).isEqualTo(1L);
        assertThat(core.amount()).isEqualByComparingTo("80.00");
        // El plazo es el cierre del gasto, no la fecha a secas: el dia del
        // vencimiento cuenta completo, igual que para decidir si fue puntual.
        assertThat(core.dueAt()).isEqualTo(dueDate.plusDays(1).atStartOfDay());
        assertThat(core.resolvedAt()).isEqualTo(payment.getPaidAt());
    }

    @Test
    void timingBadgeEventsCarryNoObligationFacts() {
        // EARLY_PAYMENT solo desbloquea una insignia. Si llevara monto y
        // contraparte, el mismo pago entraria dos veces como evidencia.
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().plusDays(10));
        ReflectionTestUtils.setField(payment, "id", 42L);
        payment.pay(new BigDecimal("80.00"));
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(any(), any())).thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        service.handle(new ConfirmPaymentCommand(42L, "owner"));

        var badge = capturedCommandOfType(ReputationEventType.EARLY_PAYMENT);
        assertThat(badge.counterpartyId()).isNull();
        assertThat(badge.amount()).isNull();
        assertThat(badge.resolvedAt()).isNull();
    }

    @Test
    void registerOverduePenaltiesSealsTheEventWhenTheDeadlineClosed() {
        LocalDate dueDate = LocalDate.now().minusDays(3);
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), dueDate);
        ReflectionTestUtils.setField(payment, "id", 42L);
        when(paymentRepository.findOverdueUnpaidPayments(any(LocalDate.class))).thenReturn(List.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(42L, ReputationEventType.OVERDUE_PAYMENT))
                .thenReturn(false);
        when(pblCommandService.handle(any(RegisterReputationEventCommand.class))).thenReturn(1L);

        assertThat(service.registerOverduePenalties()).isEqualTo(1);

        var penalty = capturedCommandOfType(ReputationEventType.OVERDUE_PAYMENT);
        assertThat(penalty.counterpartyId()).isEqualTo(1L);
        assertThat(penalty.amount()).isEqualByComparingTo("80.00");
        // La obligacion quedo resuelta cuando cerro el plazo, no ahora: un
        // vencimiento viejo detectado hoy no es evidencia fresca.
        assertThat(penalty.resolvedAt()).isEqualTo(dueDate.plusDays(1).atStartOfDay());
    }

    @Test
    void registerOverduePenaltiesSkipsPaymentThatAlreadyCarriesIt() {
        Payment payment = paymentWithExpense(new BigDecimal("80.00"), LocalDate.now().minusDays(3));
        ReflectionTestUtils.setField(payment, "id", 42L);
        when(paymentRepository.findOverdueUnpaidPayments(any(LocalDate.class))).thenReturn(List.of(payment));
        when(reputationEventRepository.existsByPaymentIdAndType(42L, ReputationEventType.OVERDUE_PAYMENT))
                .thenReturn(true);

        service.registerOverduePenalties();

        verifyNoInteractions(pblCommandService);
    }

    private RegisterReputationEventCommand capturedCommandOfType(ReputationEventType type) {
        ArgumentCaptor<RegisterReputationEventCommand> captor =
                ArgumentCaptor.forClass(RegisterReputationEventCommand.class);
        verify(pblCommandService, atLeastOnce()).handle(captor.capture());
        return captor.getAllValues().stream()
                .filter(command -> command.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se registro ningun evento " + type));
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
