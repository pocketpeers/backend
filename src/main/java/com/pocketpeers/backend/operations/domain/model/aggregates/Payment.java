package com.pocketpeers.backend.operations.domain.model.aggregates;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentEvidence;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Payment extends AuditableAbstractAggregateRoot<Payment> {

    @Getter
    private String description;

    @Getter
    private BigDecimal amount;

    @Getter
    private BigDecimal amountPaid;

    @Getter
    private Boolean confirmed;

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
        this.amountPaid = this.amountPaid.add(amount);
        if (this.amountPaid.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("Partial payment cannot exceed total amount");
        } else if (this.amountPaid.compareTo(this.amount) < 0) {
            this.status = PaymentStatus.PARTIAL;
        } else {
            this.status = PaymentStatus.COMPLETED;
        }
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
