package com.pocketpeers.backend.pbl.domain.model.entities;

import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_id"}))
public class UserBadge extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = false)
    private BadgeCatalog badge;

    @Column(nullable = false)
    private LocalDateTime unlockedAt;

    public UserBadge() {
    }

    public UserBadge(User user, BadgeCatalog badge) {
        this.user = user;
        this.badge = badge;
        this.unlockedAt = LocalDateTime.now();
    }
}
