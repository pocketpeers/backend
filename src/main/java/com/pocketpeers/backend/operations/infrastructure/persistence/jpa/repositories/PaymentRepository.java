package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @EntityGraph(attributePaths = {"evidences", "expense", "expense.group", "expense.user", "user"})
    Optional<Payment> findById(Long id);

    List<Payment> findAllByUser_Id(Long userId);
    List<Payment> findAllByExpenseId(Long expenseId);
    List<Payment> findAllByUser_IdAndStatus(Long userId, PaymentStatus status);
    Optional<Payment> findByUser_IdAndExpenseId(Long userId, Long expenseId);

    @EntityGraph(attributePaths = {"expense", "expense.group", "user"})
    @Query("""
    SELECT p
    FROM Payment p
    JOIN p.expense e
    WHERE e.dueDate.dueDate = :dueDate
      AND p.amountPaid < p.amount
""")
    List<Payment> findUnpaidPaymentsDueOn(@Param("dueDate") LocalDate dueDate);

    @Query("""
    SELECT COUNT(p)
    FROM Payment p
    JOIN p.expense e
    WHERE p.user.id = :userId
      AND e.dueDate.dueDate <= :date
      AND (p.confirmed = false OR p.amountPaid < p.amount)
""")
    long countPendingPaymentsByUserIdDueOnOrBefore(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("""
    SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
    FROM Payment p
    JOIN p.expense e
    WHERE p.user.id = :userId
      AND e.dueDate.dueDate BETWEEN :startDate AND :endDate
""")
    boolean existsPaymentDueForUserBetween(@Param("userId") Long userId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * Obtiene pagos de gastos donde el usuario es administrador del grupo relacionado.
     */
    @Query("""
    SELECT p 
    FROM Payment p 
    JOIN p.expense e 
    JOIN e.group g 
    JOIN GroupMember gm ON gm.group.id = g.id 
    WHERE gm.user.id = :user 
      AND gm.role = :role AND p.user.id != :user
""")    List<Payment> findIncomingPaymentsByUser(@Param("user") Long user, @Param("role") GroupRole role);

}
