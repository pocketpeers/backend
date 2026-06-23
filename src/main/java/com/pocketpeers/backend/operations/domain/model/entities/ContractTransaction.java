package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@Entity
public class ContractTransaction extends AuditableModel{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private ExpenseContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private com.pocketpeers.backend.operations.domain.model.aggregates.Payment payment;

    @Embedded
    private TransactionHash transactionHash;



}
