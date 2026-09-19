package com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReputationEventRepository extends JpaRepository<ReputationEvent, Long> {
    List<ReputationEvent> findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtAsc(Long userId, LocalDateTime from);
    List<ReputationEvent> findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtDesc(Long userId, LocalDateTime from);
    boolean existsByPaymentIdAndType(Long paymentId, ReputationEventType type);

    /**
     * Cuantos eventos de un tipo acumula un usuario.
     *
     * <p>La usan las insignias que se ganan por repeticion y no por un evento
     * puntual. Cuenta en base de datos en vez de traer las filas porque el
     * historial crece sin tope y aqui solo interesa el numero.</p>
     */
    long countByUser_IdAndType(Long userId, ReputationEventType type);

    // Consultas de PeerScore. Las tres piden `counterpartyId` y `amount` no
    // nulos porque son los eventos que describen el desenlace de una
    // obligacion; los demas solo desbloquean insignias y no son evidencia.
    //
    // Todas traen `user` por EntityGraph. El calculador nunca ve la entidad,
    // pero la proyeccion necesita el id del pagador para descartar los eventos
    // en que alguien es su propia contraparte, y sin el grafo cada evento
    // dispararia su propia consulta contra `users`.

    /** Obligaciones de un usuario: la evidencia con la que se calcula su score. */
    @EntityGraph(attributePaths = "user")
    List<ReputationEvent> findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(Long userId);

    /**
     * Obligaciones hacia un usuario, o sea las veces que fue el acreedor.
     *
     * <p>Es la direccion inversa que necesita el indice de reciprocidad: sirve
     * para medir cuanta evidencia devuelve cada contraparte, que es lo que
     * distingue una relacion real de un par de cuentas que se pagan entre si.</p>
     */
    @EntityGraph(attributePaths = "user")
    List<ReputationEvent> findAllByCounterpartyIdAndAmountIsNotNull(Long counterpartyId);

    /** Toda la evidencia del sistema, para las estadisticas por grupo y globales. */
    @EntityGraph(attributePaths = "user")
    List<ReputationEvent> findAllByCounterpartyIdIsNotNullAndAmountIsNotNull();
}
