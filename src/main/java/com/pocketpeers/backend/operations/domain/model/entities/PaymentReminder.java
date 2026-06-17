package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payment_reminders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_reminder_payment_type",
                columnNames = {"payment_id", "type"}
        )
)
public class PaymentReminder extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentReminderType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    private LocalDateTime readAt;

    public PaymentReminder() {
    }

    public PaymentReminder(User user, Payment payment, PaymentReminderType type, String title, String body) {
        this.user = user;
        this.payment = payment;
        this.type = type;
        this.title = title;
        this.body = body;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        this.readAt = LocalDateTime.now();
    }
}
