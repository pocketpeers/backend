package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro del contrato de gasto de la version anterior del programa.
 *
 * <p>Ya no se escribe. El programa desplegado usa la semilla {@code expense_v2}
 * y un layout de cuenta distinto, y su estado vive en {@link ExpenseChain}.
 * Esta entidad se conserva para no perder lo que se registro con el programa
 * anterior: son anclajes validos, legibles con el formato de entonces.</p>
 */
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
