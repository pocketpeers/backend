package com.pocketpeers.backend.operations.domain.model.aggregates;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentEvidence;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Payment extends AuditableAbstractAggregateRoot<Payment> {
    // Payment deadlines are business days in Lima, the same zone the reputation
    // engine and the scheduled tasks use to decide what counts as on time.
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    @Getter
    private String description;

    @Getter
    private BigDecimal amount;

    @Getter
    private BigDecimal amountPaid;

    @Getter
    private Boolean confirmed;

    // When the payer registered the latest instalment. Reputation is about the
    // payer's behaviour, so being on time has to be measured against this and
    // not against the moment the expense creator got around to confirming.
    // `updatedAt` cannot stand in for it: confirmation moves that timestamp too.
    // Null on payments registered before this field existed.
    @Getter
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Getter
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Getter
    @ManyToOne
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Getter
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentEvidence> evidences = new ArrayList<>();

    public Payment() {}

    public Payment(String description, BigDecimal amount, User user, Expense expense) {
        this.description = description;
        this.amount = amount;
        this.amountPaid = BigDecimal.ZERO;
        this.confirmed = false;
        this.status = PaymentStatus.PENDING;
        this.user = user;
        this.expense = expense;
    }

    public Payment updateInformation(String newDescription, BigDecimal newAmount){
        this.description = newDescription;
        this.amount = newAmount;
        return this;
    }

    public void pay(BigDecimal amount) {
        if (this.confirmed && this.status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Completed confirmed payments cannot be modified");
        }
        this.amountPaid = this.amountPaid.add(amount);
        if (this.amountPaid.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("Partial payment cannot exceed total amount");
        } else if (this.amountPaid.compareTo(this.amount) < 0) {
            this.status = PaymentStatus.PARTIAL;
        } else {
            this.status = PaymentStatus.COMPLETED;
        }
        // Each instalment overwrites the mark: the reputation event registered
        // on the next confirmation is about this instalment, not an earlier one.
        this.paidAt = LocalDateTime.now(LIMA_ZONE);
        this.confirmed = false;
    }

    public void confirmPayment() {
        this.confirmed = true;
    }

    public String getStatus() {
        return this.status.name().toUpperCase();
    }

    public void addEvidence(PaymentEvidence evidence) {
        this.evidences.add(evidence);
        evidence.assignToPayment(this);
    }
}
