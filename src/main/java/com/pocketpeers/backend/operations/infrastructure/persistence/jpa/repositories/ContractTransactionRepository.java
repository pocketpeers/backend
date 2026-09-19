package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.ContractTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractTransactionRepository extends JpaRepository<ContractTransaction,Long>{
    Optional<ContractTransaction> findFirstByContract_Expense_IdAndPaymentIsNullOrderByCreatedAtDesc(Long expenseId);
    Optional<ContractTransaction> findFirstByPayment_IdOrderByCreatedAtDesc(Long paymentId);

    /**
     * Cuantos gastos del grupo siguen sin su transaccion en cadena.
     *
     * <p>Existe para que la app pueda preguntar "¿ya llegaron los hashes?" con
     * una sola peticion. Antes lo averiguaba recargando la lista de gastos, la
     * de pagos, el resumen y, por cada gasto, el gasto y sus pagos: veintitantas
     * peticiones cada tres segundos, y cada una resolviendo su hash con una
     * consulta aparte. Contar aqui es una consulta y un entero.</p>
     *
     * <p>Se cuentan solo los activos, por la misma razon que
     * {@code findActiveWithoutContract}: un gasto anulado no va a recibir hash
     * nunca y dejaria el contador sin bajar de cero para siempre.</p>
     */
    @Query("""
    SELECT COUNT(e) FROM Expense e
    WHERE e.group.id = :groupId
      AND (e.active IS NULL OR e.active = 1)
      AND NOT EXISTS (
        SELECT 1 FROM ContractTransaction t
        WHERE t.contract.expense = e AND t.payment IS NULL)
""")
    long countExpensesWithoutHash(@Param("groupId") Long groupId);

    /**
     * Cuantos pagos del grupo siguen sin su transaccion en cadena.
     *
     * <p>Un pago solo puede sincronizarse despues de que exista el contrato de
     * su gasto, asi que mientras el contador de gastos no llegue a cero este
     * tampoco lo hara. Se devuelven por separado igualmente, porque saber cual
     * de los dos falta es lo que distingue "la cadena va lenta" de "el gasto
     * quedo huerfano".</p>
     */
    @Query("""
    SELECT COUNT(p) FROM Payment p
    WHERE p.expense.group.id = :groupId
      AND (p.expense.active IS NULL OR p.expense.active = 1)
      AND NOT EXISTS (
        SELECT 1 FROM ContractTransaction t WHERE t.payment = p)
""")
    long countPaymentsWithoutHash(@Param("groupId") Long groupId);
}
