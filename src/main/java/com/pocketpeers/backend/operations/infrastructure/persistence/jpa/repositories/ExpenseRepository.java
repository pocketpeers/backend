package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DueDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Gastos vigentes que se quedaron sin contrato en la cadena.
     *
     * <p>El despliegue ocurre fuera de la transaccion del gasto y en un solo
     * intento: si falla, el manejador escribe un aviso en el log y nadie
     * reintenta jamas. Ese gasto pierde su trazabilidad y, de paso, ninguno de
     * sus pagos puede sincronizarse, porque la sincronizacion de pagos exige
     * que el contrato del gasto exista.</p>
     *
     * <p>Se aceptan los anulados fuera del resultado: reintentar el registro de
     * un gasto que ya no vale seria pagar una comision por nada.</p>
     */
    @Query("""
    SELECT e FROM Expense e
    WHERE (e.active IS NULL OR e.active = 1)
      AND NOT EXISTS (SELECT 1 FROM ExpenseContract c WHERE c.expense = e)
    ORDER BY e.id
""")
    List<Expense> findActiveWithoutContract();
}
