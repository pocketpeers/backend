package com.pocketpeers.backend.operations.domain.model.aggregates;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentReceipt;
import com.pocketpeers.backend.operations.domain.model.valueobjects.Amount;
import com.pocketpeers.backend.operations.domain.model.valueobjects.Description;
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
    @Embedded
    private Description description;

    @Getter
    @Embedded
    private Amount amount;

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
    private List<PaymentReceipt> receipts = new ArrayList<>();

    public Payment() {}

    public Payment(String description, BigDecimal amount, User user, Expense expense) {
        this.description = new Description(description);
        this.amount = new Amount(amount);
        this.status = PaymentStatus.PENDING;
        this.user = user;
        this.expense = expense;
    }

    public Payment UpdateInformation(String newDescription, BigDecimal newAmount){
        this.description = new Description(newDescription);
        this.amount = new Amount(newAmount);
        return this;
    }

    public void completePayment(){
        this.status = PaymentStatus.COMPLETED;
    }

    public String getDescription() {return description.getDescription();}

    public BigDecimal getAmount(){return amount.getAmount();}

    public Amount getAmountAsObject(){return this.amount;}

    public String getStatus(){return this.status.name().toUpperCase();}

    public void addReceipt(PaymentReceipt receipt) {
        this.receipts.add(receipt);
        receipt.assignToPayment(this);
    }
}

