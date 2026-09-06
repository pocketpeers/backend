package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.domain.model.valueobjects.NotificationDeliveryStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, Long> {
    boolean existsByPayment_IdAndType(Long paymentId, PaymentReminderType type);

    @EntityGraph(attributePaths = {"payment", "payment.expense", "payment.expense.group"})
    List<PaymentReminder> findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);

    /**
     * Historial completo, leidas y no leidas.
     *
     * <p>Va paginado porque esta lista solo crece: un usuario con meses de uso
     * acumula cientos de recordatorios y traerlos todos en cada apertura de la
     * pantalla seria innecesario.</p>
     *
     * <p>El {@code EntityGraph} trae el pago, el gasto y el grupo en la misma
     * consulta. Sin el, construir cada recurso dispararia tres consultas
     * adicionales por fila.</p>
     */
    @EntityGraph(attributePaths = {"payment", "payment.expense", "payment.expense.group"})
    List<PaymentReminder> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_IdAndReadAtIsNull(Long userId);

    /** Marca como leidas todas las pendientes de un usuario, en una sola consulta. */
    @Modifying
    @Query("update PaymentReminder r set r.readAt = :now where r.user.id = :userId and r.readAt is null")
    int markAllReadForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Recordatorios de un pago que efectivamente llegaron a un dispositivo.
     *
     * <p>Es la consulta que hace falta para medir el efecto de los recordatorios:
     * comparar la conducta de quien recibio el aviso contra la de quien no, sin
     * mezclar a quienes nunca lo recibieron por no tener dispositivo registrado.</p>
     */
    long countByPayment_IdAndDeliveryStatus(Long paymentId, NotificationDeliveryStatus status);

    /** Recuento por estado de entrega, para diagnosticar cuantos avisos se pierden. */
    @Query("select r.deliveryStatus, count(r) from PaymentReminder r group by r.deliveryStatus")
    List<Object[]> countGroupedByDeliveryStatus();

    /**
     * Borra las ya leidas de un usuario.
     *
     * <p>Se restringe a las leidas a proposito: vaciar tambien las pendientes
     * haria que alguien pierda un aviso de pago que todavia no vio.</p>
     */
    @Modifying
    @Query("delete from PaymentReminder r where r.user.id = :userId and r.readAt is not null")
    int deleteReadForUser(@Param("userId") Long userId);
}
