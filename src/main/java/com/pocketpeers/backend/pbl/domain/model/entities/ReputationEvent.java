package com.pocketpeers.backend.pbl.domain.model.entities;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
public class ReputationEvent extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Long groupId;
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReputationEventType type;

    @Column(nullable = false)
    private int pointsDelta;

    @Column(nullable = false)
    private int resultingScore;

    private String description;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // Hechos crudos de la obligacion, que PeerScore necesita y los campos
    // derivados del motor anterior no conservan. `pointsDelta` y
    // `resultingScore` se mantienen como historia de ese motor, pero ya no son
    // la fuente de verdad del score.
    //
    // Los cuatro son nulos en dos casos legitimos: eventos registrados antes de
    // que estos campos existieran, y tipos que no describen el desenlace de una
    // obligacion (GROUP_CREATED, ZERO_DEBT, EARLY_PAYMENT, JUST_IN_TIME_PAYMENT),
    // para los que `PaymentOutcome.fromEventType` devuelve null.
    private Long counterpartyId;

    private BigDecimal amount;

    private LocalDateTime dueAt;

    // Cuando quedo resuelta la obligacion, que es lo que envejece la evidencia.
    // Lo provee quien registra el evento y no se toma del reloj aqui: para un
    // pago es el momento en que el usuario pago, y para un vencimiento el
    // momento en que se cerro el plazo. Fijarlo al instante de la confirmacion
    // haria que la demora del acreedor cambiara la antiguedad del hecho.
    private LocalDateTime resolvedAt;

    public ReputationEvent() {
    }

    public ReputationEvent(User user, Long groupId, Long paymentId, ReputationEventType type, int pointsDelta,
                           int resultingScore, String description, Long counterpartyId, BigDecimal amount,
                           LocalDateTime dueAt, LocalDateTime resolvedAt) {
        this.user = user;
        this.groupId = groupId;
        this.paymentId = paymentId;
        this.type = type;
        this.pointsDelta = pointsDelta;
        this.resultingScore = resultingScore;
        this.description = description;
        this.counterpartyId = counterpartyId;
        this.amount = amount;
        this.dueAt = dueAt;
        this.resolvedAt = resolvedAt;
        this.occurredAt = LocalDateTime.now();
    }
}
