package com.pocketpeers.backend.pbl.domain.model.entities;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

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

    public ReputationEvent() {
    }

    public ReputationEvent(User user, Long groupId, Long paymentId, ReputationEventType type, int pointsDelta,
                           int resultingScore, String description) {
        this.user = user;
        this.groupId = groupId;
        this.paymentId = paymentId;
        this.type = type;
        this.pointsDelta = pointsDelta;
        this.resultingScore = resultingScore;
        this.description = description;
        this.occurredAt = LocalDateTime.now();
    }
}
