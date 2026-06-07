package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class ExpenseContract extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Embedded
    private ContractAddress contractAddress;

    public ExpenseContract() {}
    public ExpenseContract(ContractAddress contractAddress, Expense expense) {
        this.contractAddress = contractAddress;
        this.expense = expense;
    }
}
