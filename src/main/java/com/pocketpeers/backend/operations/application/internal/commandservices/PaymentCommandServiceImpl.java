package com.pocketpeers.backend.operations.application.internal.commandservices;

import com.pocketpeers.backend.operations.domain.exceptions.UserNotFoundException;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.commands.CompletePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.domain.services.PaymentCommandService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PaymentCommandServiceImpl implements PaymentCommandService {
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentCommandServiceImpl(PaymentRepository paymentRepository, ExpenseRepository expenseRepository, UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

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
    public Long handle(CompletePaymentCommand command){
        paymentRepository.findById(command.paymentId()).map(payment -> {
            payment.completePayment();
            paymentRepository.save(payment);

            applicationEventPublisher.publishEvent(new PaymentUpdatedEvent(payment));

            return command.paymentId();
        }).orElseThrow(() -> new RuntimeException("Payment not found"));
        return null;
    }
}
