package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseChainRepository extends JpaRepository<ExpenseChain, Long> {
    Optional<ExpenseChain> findByExpense(Expense expense);
    Optional<ExpenseChain> findByExpense_Id(Long expenseId);
}
