package com.pocketpeers.backend.operations.domain.model.aggregates;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.valueobjects.*;
import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
public class Expense extends AuditableAbstractAggregateRoot<Expense> {

    @Embedded
    private ExpenseName name;

    @Embedded
    private Amount amount;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @Getter
    @Embedded
    private DueDate dueDate;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseReceipt> receipts = new ArrayList<>();

    public Expense(String name, BigDecimal amount, User user, Group group, LocalDate dueDate) {
        this.name = new ExpenseName(name);
        this.amount = new Amount(amount);
        this.user = user;
        this.group = group;
        this.dueDate = new DueDate(dueDate);
    }


    public Expense() {
    }

    public void UpdateExpenseName(String newName) {
        this.name = new ExpenseName(newName);
    }

    public void UpdateAmount(BigDecimal newAmount) {
        this.amount = new Amount(newAmount);
    }

    public Expense UpdateInformation(String newName, BigDecimal newAmount, LocalDate newDueDate) {
        this.name = new ExpenseName(newName);
        this.amount = new Amount(newAmount);
        this.dueDate = new DueDate(newDueDate);
        return this;
    }

    public String getName() {
        return name.getName();
    }

    public BigDecimal getAmount() {
        return amount.getAmount();
    }

    public LocalDate getDueDate() {
        return dueDate.getDueDate();
    }

    public Amount getAmountAsObject() {
        return amount;
    }

    public void addReceipt(ExpenseReceipt receipt) {
        receipts.add(receipt);
        receipt.assignToExpense(this);
    }

    public String getStatus() {
        if (this.getTotalPaidAmount().equals(this.amount.getAmount())) {
            return ExpenseStatus.COMPLETED.name().toLowerCase();
        } else {
            return ExpenseStatus.PENDING.name().toLowerCase();
        }
    }

    public BigDecimal getRemainingAmount() {
        BigDecimal totalPayed = BigDecimal.ZERO;

        return amount.getAmount().subtract(this.getTotalPaidAmount());
    }

    public BigDecimal getTotalPaidAmount() {
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Payment payment : payments) {
            if (payment.getStatus().equals(PaymentStatus.COMPLETED.name().toLowerCase())) {
                totalPaid = totalPaid.add(payment.getAmount());
            }
        }
        return totalPaid;
    }
}