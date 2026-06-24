package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.ContractTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractTransactionRepository extends JpaRepository<ContractTransaction,Long>{
    Optional<ContractTransaction> findFirstByContract_Expense_IdAndPaymentIsNullOrderByCreatedAtDesc(Long expenseId);
    Optional<ContractTransaction> findFirstByPayment_IdOrderByCreatedAtDesc(Long paymentId);
}
