package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.ExpenseChainRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseChainRecordRepository extends JpaRepository<ExpenseChainRecord, Long> {

    /** El eslabon de creacion del gasto: el unico sin pago asociado. */
    Optional<ExpenseChainRecord> findFirstByChain_Expense_IdAndPaymentIsNullOrderByCreatedAtDesc(Long expenseId);

    /** El movimiento mas reciente de un pago, que es el que refleja su estado actual. */
    Optional<ExpenseChainRecord> findFirstByPayment_IdOrderByRecordIndexDesc(Long paymentId);

    /**
     * Busca un movimiento ya registrado por su linea exacta.
     *
     * <p>Un pago puede tener varios movimientos, asi que su id por si solo no
     * identifica cual. La linea si: resume los importes y el estado con los que
     * se escribio, de modo que dos intentos del mismo movimiento la comparten y
     * dos movimientos distintos no.</p>
     */
    Optional<ExpenseChainRecord> findFirstByPayment_IdAndPaymentLineHash(Long paymentId, String paymentLineHash);

    /**
     * Cuantos gastos del grupo siguen sin su transaccion en cadena.
     *
     * <p>Existe para que la app pueda preguntar "¿ya llegaron los hashes?" con
     * una sola peticion. Antes lo averiguaba recargando la lista de gastos, la
     * de pagos, el resumen y, por cada gasto, el gasto y sus pagos: veintitantas
     * peticiones cada tres segundos, y cada una resolviendo su hash con una
     * consulta aparte. Contar aqui es una consulta y un entero.</p>
     *
     * <p>Se cuentan solo los activos: un gasto anulado no va a recibir hash
     * nunca y dejaria el contador sin bajar de cero para siempre.</p>
     */
    @Query("""
    SELECT COUNT(e) FROM Expense e
    WHERE e.group.id = :groupId
      AND (e.active IS NULL OR e.active = 1)
      AND NOT EXISTS (
        SELECT 1 FROM ExpenseChainRecord r
        WHERE r.chain.expense = e AND r.payment IS NULL)
""")
    long countExpensesWithoutHash(@Param("groupId") Long groupId);

    /**
     * Cuantos pagos del grupo siguen sin su transaccion en cadena.
     *
     * <p>Un pago solo puede anclarse despues de que exista la cadena de su
     * gasto, asi que mientras el contador de gastos no llegue a cero este
     * tampoco lo hara. Se devuelven por separado igualmente, porque saber cual
     * de los dos falta es lo que distingue "la cadena va lenta" de "el gasto
     * quedo huerfano".</p>
     */
    @Query("""
    SELECT COUNT(p) FROM Payment p
    WHERE p.expense.group.id = :groupId
      AND (p.expense.active IS NULL OR p.expense.active = 1)
      AND NOT EXISTS (
        SELECT 1 FROM ExpenseChainRecord r WHERE r.payment = p)
""")
    long countPaymentsWithoutHash(@Param("groupId") Long groupId);
}
