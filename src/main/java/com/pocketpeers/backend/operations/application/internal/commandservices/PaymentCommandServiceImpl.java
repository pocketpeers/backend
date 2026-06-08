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
import com.pocketpeers.backend.operations.domain.services.PaymentCommandService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentCommandServiceImpl implements PaymentCommandService {
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentCommandServiceImpl(PaymentRepository paymentRepository, ExpenseRepository expenseRepository,
                                     UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
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
        paymentRepository.findById(command.paymentId()).map(payment -> {
            payment.pay(command.amount());
            PaymentEvidence evidence = new PaymentEvidence(payment, command.photo());
            payment.addEvidence(evidence);
            paymentRepository.save(payment);
            applicationEventPublisher.publishEvent(new PaymentUpdatedEvent(payment));
            return payment.getId();
        }).orElseThrow(() -> new RuntimeException("Payment not found"));
        return null;
    }

    @Override
    public Long handle(ConfirmPaymentCommand command) {
        paymentRepository.findById(command.paymentId()).map(payment -> {
            Expense expense = payment.getExpense();
            if (!expense.getUser().getUsername().equals(command.username())) {
                throw new RuntimeException("Only the creator of the expense can confirm the payment");
            }
            payment.confirmPayment();
            paymentRepository.save(payment);
            applicationEventPublisher.publishEvent(new PaymentUpdatedEvent(payment));
            return payment.getId();
        }).orElseThrow(() -> new RuntimeException("Payment not found"));
        return null;
    }
}
