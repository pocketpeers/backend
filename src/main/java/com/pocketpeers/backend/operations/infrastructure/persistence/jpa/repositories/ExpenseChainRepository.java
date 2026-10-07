package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseChain;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseChainRepository extends JpaRepository<ExpenseChain, Long> {
    Optional<ExpenseChain> findByExpense(Expense expense);
    Optional<ExpenseChain> findByExpense_Id(Long expenseId);

    /**
     * La cadena del gasto, bloqueada hasta que termine la transaccion.
     *
     * <p>Es lo que pone en fila a los movimientos de un mismo gasto. Sin el
     * bloqueo, dos manejadores asincronos leian la cadena a la vez, los dos
     * calculaban el siguiente eslabon a partir del mismo estado y los dos lo
     * enviaban: quedaban dos eslabones con la misma posicion, la base y la
     * cadena dejaban de coincidir y desde ahi el gasto rechazaba cualquier
     * movimiento nuevo. Paso en produccion con dos movimientos separados por
     * menos de medio segundo.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ExpenseChain c where c.expense.id = :expenseId")
    Optional<ExpenseChain> lockByExpenseId(@Param("expenseId") Long expenseId);
}
