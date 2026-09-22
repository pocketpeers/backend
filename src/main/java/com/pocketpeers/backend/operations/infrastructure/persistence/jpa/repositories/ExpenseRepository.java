package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DueDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Optional<Expense> findByName(String name);
    Optional<Expense> findByNameAndUser_Id(String name, Long userId);
    List<Expense> findAllByNameIgnoreCase(String name);
    List<Expense> findAllByNameContainingIgnoreCase(String name);
    List<Expense> findByUser_Id(Long userId);

    /**
     * Gastos en los que el usuario participa: los que creo y aquellos donde le
     * toca pagar.
     *
     * <p>{@code findByUser_Id} devuelve solo los creados, y eso bastaba mientras
     * unicamente el administrador podia crear gastos. Desde que cualquier
     * integrante puede hacerlo, a un deudor le asignan cuotas en gastos que no
     * son suyos: su pago existe, pero el gasto donde vive la fecha de
     * vencimiento no estaba en ninguna lista que la aplicacion pidiera, asi que
     * "Vence pronto" descartaba ese pago sin decir nada.</p>
     *
     * <p>Es un superconjunto de {@code findByUser_Id}: quien lo consuma puede
     * quedarse con los propios filtrando por {@code user.id} y ahorrarse una
     * segunda peticion.</p>
     */
    @Query("""
    SELECT DISTINCT e FROM Expense e
    LEFT JOIN e.payments p
    WHERE (e.active IS NULL OR e.active = 1)
      AND (e.user.id = :userId OR p.user.id = :userId)
    ORDER BY e.id
""")
    List<Expense> findWhereUserParticipates(@Param("userId") Long userId);
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
     *
     * <p>El corte por antiguedad es lo que permite reconciliar seguido. El
     * despliegue es asincrono y tarda: mientras corre, el gasto todavia no
     * tiene contrato y esta consulta lo devolveria, de modo que la pasada
     * siguiente lanzaria un segundo despliegue del mismo gasto y pagaria dos
     * veces la comision. Antes eso se evitaba espaciando las pasadas quince
     * minutos, que es tanto como decir que un gasto caido esperaba hasta
     * quince minutos a que alguien lo recogiera. Con el corte las dos cosas
     * dejan de estar atadas: solo entran los gastos que llevan el suficiente
     * tiempo sin contrato como para que ningun despliegue pueda seguir en
     * vuelo.</p>
     *
     * @param creadosAntesDe solo se devuelven los gastos creados antes de este
     *                       instante
     */
    @Query("""
    SELECT e FROM Expense e
    WHERE (e.active IS NULL OR e.active = 1)
      AND e.createdAt < :creadosAntesDe
      AND NOT EXISTS (SELECT 1 FROM ExpenseChain c WHERE c.expense = e)
    ORDER BY e.id
""")
    List<Expense> findActiveWithoutContract(@Param("creadosAntesDe") Date creadosAntesDe);
}
