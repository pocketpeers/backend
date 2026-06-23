package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DueDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Optional<Expense> findByName(String name);
    Optional<Expense> findByNameAndUser_Id(String name, Long userId);
    List<Expense> findAllByNameIgnoreCase(String name);
    List<Expense> findAllByNameContainingIgnoreCase(String name);
    List<Expense> findByUser_Id(Long userId);
    List<Expense> findByGroupId(Long groupId);
    List<Expense> findAllByDueDate(DueDate dueDate);
}
