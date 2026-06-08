package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@PrimaryKeyJoinColumn(name = "receipt_id")
public class ExpenseReceipt extends Receipt {
    @ManyToOne()
    @JoinColumn(name = "expense_id")
    private Expense expense;

    public ExpenseReceipt() {}

    public ExpenseReceipt(String name, BigDecimal amount, LocalDate issueDate, String imagePath, Expense expense) {
        super(name, amount, issueDate, imagePath);
        this.expense = expense;
    }
    public ExpenseReceipt(String name, String receiptNumber, BigDecimal amount, LocalDate issueDate, String imagePath, Expense expense) {
        super(name, amount, issueDate, imagePath, receiptNumber);
        this.expense = expense;
    }

    public void assignToExpense(Expense expense) {
        this.expense = expense;
    }
}
