package com.pocketpeers.backend.operations.application.internal.commandservices;

import com.pocketpeers.backend.operations.domain.exceptions.UserNotFoundException;
import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.commands.ConfirmPaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.MakePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentEvidence;
import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.services.PaymentCommandService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentEvidenceRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class PaymentCommandServiceImpl implements PaymentCommandService {
    // Payment deadlines and timing badges are evaluated with Lima business time
    // so the backend and users share the same day boundary.
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentEvidenceRepository paymentEvidenceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PblCommandService pblCommandService;
    private final ReputationEventRepository reputationEventRepository;

    public PaymentCommandServiceImpl(PaymentRepository paymentRepository, ExpenseRepository expenseRepository,
                                     PaymentEvidenceRepository paymentEvidenceRepository,
                                     UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher,
                                     PblCommandService pblCommandService,
                                     ReputationEventRepository reputationEventRepository) {
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.paymentEvidenceRepository = paymentEvidenceRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.pblCommandService = pblCommandService;
        this.reputationEventRepository = reputationEventRepository;
    }

    @Override
    public Long handle(CreatePaymentCommand command) {
        var expense =  expenseRepository.findById(command.expenseId())
                .orElseThrow(()-> new RuntimeException("Expense not found"));
        if (!expense.isActive()) {
            throw new RuntimeException("Cancelled expenses cannot receive payments");
        }
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));
        Payment payment = new Payment(command.description(), command.amount(), user, expense);
        payment = paymentRepository.save(payment);
        applicationEventPublisher.publishEvent(new PaymentCreatedEvent(payment));

        return payment.getId();
    }

    @Override
    @Transactional
    public Long handle(MakePaymentCommand command){
        return paymentRepository.findById(command.paymentId()).map(payment -> {
            if (!payment.getExpense().isActive()) {
                throw new RuntimeException("Cancelled expenses cannot receive payments");
            }
            if (payment.getConfirmed() && payment.getStatus().equals(PaymentStatus.COMPLETED.name())) {
                throw new RuntimeException("Completed confirmed payments cannot be modified");
            }
            payment.pay(command.amount());
            if (command.photo() != null && !command.photo().isBlank()) {
                PaymentEvidence evidence = new PaymentEvidence(payment, command.photo());
                payment.addEvidence(evidence);
                paymentEvidenceRepository.save(evidence);
            }
            paymentRepository.save(payment);
            applicationEventPublisher.publishEvent(new PaymentUpdatedEvent(payment));
            return payment.getId();
        }).orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    @Override
    @Transactional
    public Long handle(ConfirmPaymentCommand command) {
        return paymentRepository.findById(command.paymentId()).map(payment -> {
            // Only the expense creator can confirm money received. Confirmation
            // is the point where reputation, badges and blockchain status move.
            Expense expense = payment.getExpense();
            if (!expense.isActive()) {
                throw new RuntimeException("Cancelled expenses cannot confirm payments");
            }
            if (!expense.getUser().getUsername().equals(command.username())) {
                throw new RuntimeException("Only the creator of the expense can confirm the payment");
            }
            if (payment.getConfirmed()) {
                throw new RuntimeException("Payment is already confirmed");
            }
            if (payment.getStatus().equals("PENDING")) {
                throw new RuntimeException("Only registered payments can be confirmed");
            }
            payment.confirmPayment();
            paymentRepository.save(payment);
            registerReputationEventForConfirmedPayment(payment);
            applicationEventPublisher.publishEvent(new PaymentUpdatedEvent(payment));
            return payment.getId();
        }).orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    private void registerReputationEventForConfirmedPayment(Payment payment) {
        // A confirmed payment can produce one core reputation event plus
        // optional timing badge events. Duplicate checks keep retries safe.
        var type = reputationEventTypeFor(payment);
        if (isOverdue(payment)) {
            registerOverduePaymentPenaltyIfMissing(payment);
        }
        if (!shouldSkipReputationEvent(payment, type)) {
            pblCommandService.handle(outcomeCommand(payment, type, paymentMoment(payment)));
        }
        registerTimingBadgeEvents(payment);
    }

    @Override
    @Transactional
    public int registerOverduePenalties() {
        // Vive aqui y no en el planificador porque los hechos que el evento
        // necesita (acreedor, monto, plazo) se leen del pago, y este es el
        // servicio que sabe como leerlos. Tenerlo en dos lugares garantizaba
        // que un dia registraran evidencia distinta para el mismo hecho.
        var overdue = paymentRepository.findOverdueUnpaidPayments(LocalDate.now(LIMA_ZONE));
        overdue.forEach(this::registerOverduePaymentPenaltyIfMissing);
        return overdue.size();
    }

    /**
     * Comando de un desenlace, con los hechos que PeerScore necesita.
     *
     * <p>La contraparte es quien creo el gasto: es a quien se le debe. El plazo
     * es el cierre del gasto y no la fecha a secas, para que coincida con el
     * limite que decide si un pago fue puntual.</p>
     */
    private RegisterReputationEventCommand outcomeCommand(Payment payment, ReputationEventType type,
                                                          LocalDateTime resolvedAt) {
        return new RegisterReputationEventCommand(
                payment.getUser().getId(),
                payment.getExpense().getGroup().getId(),
                payment.getId(),
                type,
                descriptionFor(type),
                payment.getExpense().getUser().getId(),
                payment.getAmount(),
                expenseCloseOf(payment),
                resolvedAt
        );
    }

    private boolean isOverdue(Payment payment) {
        return payment.getExpense().getDueDate().isBefore(paymentMoment(payment).toLocalDate());
    }

    private LocalDateTime paymentMoment(Payment payment) {
        // Timing is judged by when the payer paid, not by when this method runs.
        // Confirmation depends on the expense creator, and making reputation
        // depend on it let a late confirmation turn an on-time payment into an
        // overdue one. Payments registered before `paidAt` existed have no mark,
        // and for those the confirmation time is still the best guess available.
        var paidAt = payment.getPaidAt();
        return paidAt != null ? paidAt : LocalDateTime.now(LIMA_ZONE);
    }

    private void registerOverduePaymentPenaltyIfMissing(Payment payment) {
        if (reputationEventRepository.existsByPaymentIdAndType(payment.getId(), ReputationEventType.OVERDUE_PAYMENT)) {
            return;
        }
        // La obligacion quedo resuelta, como incumplida, cuando se cerro el
        // plazo. Sellarla con el instante en que corre este metodo haria que un
        // vencimiento antiguo detectado hoy pareciera evidencia fresca.
        pblCommandService.handle(outcomeCommand(payment, ReputationEventType.OVERDUE_PAYMENT,
                expenseCloseOf(payment)));
    }

    private ReputationEventType reputationEventTypeFor(Payment payment) {
        // Partial, late and on-time payments have different reputation effects.
        // Late payments are separated from overdue penalties so users can still
        // recover some points after completing the debt.
        if (payment.getStatus().equals("PARTIAL")) {
            return ReputationEventType.PARTIAL_PAYMENT;
        }
        if (isOverdue(payment)) {
            return ReputationEventType.LATE_PAYMENT;
        }
        return ReputationEventType.ON_TIME_PAYMENT;
    }

    private boolean shouldSkipReputationEvent(Payment payment, ReputationEventType type) {
        // Core reputation events should be recorded once per payment status
        // path, while badge-only timing events are handled separately.
        if (type == ReputationEventType.PARTIAL_PAYMENT) {
            return reputationEventRepository.existsByPaymentIdAndType(payment.getId(), ReputationEventType.PARTIAL_PAYMENT);
        }
        if (type == ReputationEventType.ON_TIME_PAYMENT) {
            return paymentHasCoreReputationEvent(payment);
        }
        return reputationEventRepository.existsByPaymentIdAndType(payment.getId(), type);
    }

    private boolean paymentHasCoreReputationEvent(Payment payment) {
        return reputationEventRepository.existsByPaymentIdAndType(payment.getId(), ReputationEventType.PARTIAL_PAYMENT)
                || reputationEventRepository.existsByPaymentIdAndType(payment.getId(), ReputationEventType.ON_TIME_PAYMENT)
                || reputationEventRepository.existsByPaymentIdAndType(payment.getId(), ReputationEventType.OVERDUE_PAYMENT)
                || reputationEventRepository.existsByPaymentIdAndType(payment.getId(), ReputationEventType.LATE_PAYMENT);
    }

    private void registerTimingBadgeEvents(Payment payment) {
        // Timing events do not change score directly; they exist to unlock
        // achievements such as early payment or just-in-time payment.
        var timeUntilExpenseCloses = timeUntilExpenseCloses(payment);
        if (timeUntilExpenseCloses.compareTo(Duration.ofHours(48)) > 0) {
            registerPaymentBadgeEventIfMissing(payment, ReputationEventType.EARLY_PAYMENT,
                    "Payment made more than 48 hours before expense close");
        }
        if (!timeUntilExpenseCloses.isNegative()
                && !timeUntilExpenseCloses.isZero()
                && timeUntilExpenseCloses.compareTo(Duration.ofHours(1)) < 0) {
            registerPaymentBadgeEventIfMissing(payment, ReputationEventType.JUST_IN_TIME_PAYMENT,
                    "Payment made less than one hour before expense close");
        }
    }

    private Duration timeUntilExpenseCloses(Payment payment) {
        return Duration.between(paymentMoment(payment), expenseCloseOf(payment));
    }

    /**
     * Instante en que se cierra el plazo de un gasto.
     *
     * <p>Es el dia siguiente al vencimiento a las 00:00, o sea que el dia del
     * vencimiento cuenta completo. Es el mismo limite que usa la deteccion de
     * atrasos, y por eso es el que se guarda como plazo del evento.</p>
     */
    private LocalDateTime expenseCloseOf(Payment payment) {
        return payment.getExpense().getDueDate().plusDays(1).atStartOfDay();
    }

    private void registerPaymentBadgeEventIfMissing(Payment payment, ReputationEventType type, String description) {
        if (reputationEventRepository.existsByPaymentIdAndType(payment.getId(), type)) {
            return;
        }
        pblCommandService.handle(RegisterReputationEventCommand.badgeOnly(
                payment.getUser().getId(),
                payment.getExpense().getGroup().getId(),
                payment.getId(),
                type,
                description
        ));
    }

    private String descriptionFor(ReputationEventType type) {
        return switch (type) {
            case PARTIAL_PAYMENT -> "Partial payment confirmed by expense creator";
            case OVERDUE_PAYMENT -> "Payment became overdue before being completed";
            case ON_TIME_PAYMENT -> "Full payment confirmed in one installment";
            case LATE_PAYMENT -> "Late payment completed after overdue penalty";
            case MANUAL_ADJUSTMENT -> "Manual reputation adjustment";
            case EARLY_PAYMENT -> "Payment made more than 48 hours before expense close";
            case GROUP_CREATED -> "Collaborative microfinance group created successfully";
            case JUST_IN_TIME_PAYMENT -> "Payment made less than one hour before expense close";
            case ZERO_DEBT -> "Month closed with no pending debts or commitments";
        };
    }
}
