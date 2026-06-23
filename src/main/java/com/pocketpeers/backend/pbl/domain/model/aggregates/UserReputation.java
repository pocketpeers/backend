package com.pocketpeers.backend.pbl.domain.model.aggregates;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
public class UserReputation extends AuditableAbstractAggregateRoot<UserReputation> {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int onTimePaymentStreak;

    @Column(nullable = false)
    private int completedPayments;

    public UserReputation() {
    }

    public UserReputation(User user) {
        this.user = user;
        this.score = 0;
        this.onTimePaymentStreak = 0;
        this.completedPayments = 0;
    }

    public int applyDelta(int delta) {
        this.score = Math.max(0, Math.min(100, this.score + delta));
        return this.score;
    }

    public void registerOnTimePayment() {
        this.onTimePaymentStreak++;
        this.completedPayments++;
    }

    public void registerPartialPayment() {
    }

    public void registerLatePayment() {
        this.onTimePaymentStreak = 0;
        this.completedPayments++;
    }

    public ReputationLevel getLevel() {
        return ReputationLevel.fromScore(score);
    }

    public int getPointsToNextLevel() {
        return ReputationLevel.pointsToNextLevel(score);
    }
}
