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
    /**
     * Pagos de todos los gastos de un grupo.
     *
     * <p>El grafo de entidades trae de una vez el gasto, su grupo y el deudor,
     * que es lo que el recurso necesita para serializarse. Sin el, con
     * open-in-view activo cada pago dispararia sus propias consultas al
     * convertirse a JSON y el remedio saldria peor que la enfermedad.</p>
     */
    @EntityGraph(attributePaths = {"evidences", "expense", "expense.group", "expense.user", "user"})
    List<Payment> findAllByExpense_Group_Id(Long groupId);

    @EntityGraph(attributePaths = {"evidences", "expense", "expense.group", "expense.user", "user"})
    Optional<Payment> findById(Long id);

    List<Payment> findAllByUser_Id(Long userId);
    @EntityGraph(attributePaths = {"evidences", "expense", "expense.group", "expense.user", "user"})
    List<Payment> findAllByExpenseId(Long expenseId);
    List<Payment> findAllByUser_IdAndStatus(Long userId, PaymentStatus status);
    Optional<Payment> findByUser_IdAndExpenseId(Long userId, Long expenseId);

    @EntityGraph(attributePaths = {"expense", "expense.group", "user"})
    @Query("""
    SELECT p
    FROM Payment p
    JOIN p.expense e
    WHERE e.dueDate.dueDate = :dueDate
      AND (e.active IS NULL OR e.active = 1)
      AND p.amountPaid < p.amount
""")
    List<Payment> findUnpaidPaymentsDueOn(@Param("dueDate") LocalDate dueDate);

    @EntityGraph(attributePaths = {"expense", "expense.group", "user"})
    @Query("""
    SELECT p
    FROM Payment p
    JOIN p.expense e
    WHERE e.dueDate.dueDate < :today
      AND (e.active IS NULL OR e.active = 1)
      AND p.amountPaid < p.amount
""")
    List<Payment> findOverdueUnpaidPayments(@Param("today") LocalDate today);

    @EntityGraph(attributePaths = {"expense", "expense.group", "user", "user.userInformation"})
    @Query("""
    SELECT p
    FROM Payment p
    JOIN p.expense e
    WHERE e.group.id = :groupId
      AND (e.active IS NULL OR e.active = 1)
      AND e.dueDate.dueDate < :today
      AND p.amountPaid < p.amount
    ORDER BY e.dueDate.dueDate ASC
""")
    List<Payment> findOverduePaymentsByGroupId(@Param("groupId") Long groupId,
                                                @Param("today") LocalDate today);

    @EntityGraph(attributePaths = {"expense", "expense.group", "user", "user.userInformation"})
    @Query("""
    SELECT p
    FROM Payment p
    JOIN p.expense e
    WHERE e.group.id = :groupId
      AND p.user.id = :userId
      AND (e.active IS NULL OR e.active = 1)
      AND e.dueDate.dueDate < :today
      AND p.amountPaid < p.amount
    ORDER BY e.dueDate.dueDate ASC
""")
    List<Payment> findOverduePaymentsByGroupIdAndUserId(@Param("groupId") Long groupId,
                                                         @Param("userId") Long userId,
                                                         @Param("today") LocalDate today);

    @Query("""
    SELECT COUNT(p)
    FROM Payment p
    JOIN p.expense e
    WHERE p.user.id = :userId
      AND (e.active IS NULL OR e.active = 1)
      AND e.dueDate.dueDate <= :date
      AND (p.confirmed = false OR p.amountPaid < p.amount)
""")
    long countPendingPaymentsByUserIdDueOnOrBefore(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("""
    SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
    FROM Payment p
    JOIN p.expense e
    WHERE p.user.id = :userId
      AND (e.active IS NULL OR e.active = 1)
      AND e.dueDate.dueDate BETWEEN :startDate AND :endDate
""")
    boolean existsPaymentDueForUserBetween(@Param("userId") Long userId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * Cobros del usuario: pagos de gastos que el creo y que le debe otra persona.
     *
     * <p>Antes preguntaba por el rol de administrador del grupo, y eso
     * funcionaba solo mientras el administrador fuera el unico capaz de crear
     * gastos. Desde que cualquier miembro puede crearlos, la consulta fallaba
     * en los dos sentidos: al miembro que crea un gasto no le mostraba lo que
     * le deben, y al administrador le mostraba como cobros suyos los pagos de
     * gastos creados por otros. Quien cobra es el creador del gasto, que es
     * ademas el unico que puede confirmar esos pagos.</p>
     */
    @Query("""
    SELECT p
    FROM Payment p
    JOIN p.expense e
    WHERE e.user.id = :user
      AND (e.active IS NULL OR e.active = 1)
      AND p.user.id != :user
""")
    List<Payment> findIncomingPaymentsByUser(@Param("user") Long user);

}
