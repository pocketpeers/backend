package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, Long> {
    boolean existsByPayment_IdAndType(Long paymentId, PaymentReminderType type);

    @EntityGraph(attributePaths = {"payment", "payment.expense", "payment.expense.group"})
    List<PaymentReminder> findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);
}
