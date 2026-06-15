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

    private String name;

    private BigDecimal amount;

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
        this.name = name;
        this.amount = amount;
        this.user = user;
        this.group = group;
        this.dueDate = new DueDate(dueDate);
    }


    public Expense() {
    }


    public Expense UpdateInformation(String newName, BigDecimal newAmount, LocalDate newDueDate) {
        this.name = newName;
        this.amount = newAmount;
        this.dueDate = new DueDate(newDueDate);
        return this;
    }

    public LocalDate getDueDate() {
        return dueDate.getDueDate();
    }

    public void addReceipt(ExpenseReceipt receipt) {
        receipts.add(receipt);
        receipt.assignToExpense(this);
    }

    public String getStatus() {
        if (this.getTotalPaidAmount().equals(this.amount)) {
            return ExpenseStatus.COMPLETED.name().toLowerCase();
        } else {
            return ExpenseStatus.PENDING.name().toLowerCase();
        }
    }

    public BigDecimal getRemainingAmount() {
        return amount.subtract(this.getTotalPaidAmount());
    }

    public BigDecimal getTotalPaidAmount() {
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Payment payment : payments) {
            if (payment.getConfirmed()) {
                totalPaid = totalPaid.add(payment.getAmountPaid());
            }
        }
        return totalPaid;
    }
}
