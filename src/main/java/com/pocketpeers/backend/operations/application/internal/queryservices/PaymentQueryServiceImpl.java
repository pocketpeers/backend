package com.pocketpeers.backend.operations.application.internal.queryservices;

import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.queries.*;
import com.pocketpeers.backend.operations.domain.services.PaymentQueryService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;


    @Override
    public List<Payment> handle(GetAllPaymentsQuery query){
        return paymentRepository.findAll().stream().filter(this::isActiveExpensePayment).toList();
    }

    @Override
    public Optional<Payment> handle(GetPaymentByIdQuery query){
        return paymentRepository.findById(query.paymentId());
    }

    @Override
        public List<Payment> handle(GetAllPaymentsByUserIdQuery query){
        return paymentRepository.findAllByUser_Id(query.userId()).stream().filter(this::isActiveExpensePayment).toList();
    }

    @Override
    public List<Payment> handle(GetAllPaymentsByExpenseIdQuery query){
        return paymentRepository.findAllByExpenseId(query.expenseId()).stream().filter(this::isActiveExpensePayment).toList();
    }

    @Override
    public Optional<Payment> handle(GetPaymentByUserIdAndExpenseId query){
        return paymentRepository.findByUser_IdAndExpenseId(query.userId(), query.expenseId());
    }

    @Override
    public List<Payment> handle(GetAllPaymentsByUserIdAndStatusQuery query){
        return paymentRepository.findAllByUser_IdAndStatus(query.userId(), query.status()).stream()
                .filter(this::isActiveExpensePayment)
                .toList();
    }

    @Override
    public List<Payment> handle(GetIncomingPaymentsByUserIdQuery query) {
        var user = this.userRepository.findById(query.userId());
        if(user.isEmpty())
            throw new IllegalArgumentException("User information not found for ID: " + query.userId());
        var payments = paymentRepository.findIncomingPaymentsByUser(
                user.get().getId(),
                GroupRole.ADMIN
        );
        return payments.stream().filter(this::isActiveExpensePayment).toList();
    }

    private boolean isActiveExpensePayment(Payment payment) {
        return payment.getExpense() != null && payment.getExpense().isActive();
    }

}
