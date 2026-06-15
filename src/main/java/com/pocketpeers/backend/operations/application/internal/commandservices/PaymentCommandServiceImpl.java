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
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PaymentCommandServiceImpl implements PaymentCommandService {
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PblCommandService pblCommandService;

    public PaymentCommandServiceImpl(PaymentRepository paymentRepository, ExpenseRepository expenseRepository,
                                     UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher,
                                     PblCommandService pblCommandService) {
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.pblCommandService = pblCommandService;
    }

    @Override
    public Long handle(CreatePaymentCommand command) {
        var expense =  expenseRepository.findById(command.expenseId())
                .orElseThrow(()-> new RuntimeException("Expense not found"));
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
            if (payment.getConfirmed() && payment.getStatus().equals(PaymentStatus.COMPLETED.name())) {
                throw new RuntimeException("Completed confirmed payments cannot be modified");
            }
            payment.pay(command.amount());
            PaymentEvidence evidence = new PaymentEvidence(payment, command.photo());
            payment.addEvidence(evidence);
            paymentRepository.save(payment);
            applicationEventPublisher.publishEvent(new PaymentUpdatedEvent(payment));
            return payment.getId();
        }).orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    @Override
    @Transactional
    public Long handle(ConfirmPaymentCommand command) {
        return paymentRepository.findById(command.paymentId()).map(payment -> {
            Expense expense = payment.getExpense();
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
        var type = payment.getStatus().equals("PARTIAL")
                ? ReputationEventType.PARTIAL_PAYMENT
                : payment.getExpense().getDueDate().isBefore(LocalDate.now())
                ? ReputationEventType.LATE_PAYMENT
                : ReputationEventType.ON_TIME_PAYMENT;
        pblCommandService.handle(new RegisterReputationEventCommand(
                payment.getUser().getId(),
                payment.getExpense().getGroup().getId(),
                payment.getId(),
                type,
                "Payment confirmed by expense creator"
        ));
    }
}
