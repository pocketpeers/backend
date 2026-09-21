package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * La cadena de un gasto anclada en Solana.
 *
 * <p>Tabla nueva a proposito, separada de la del programa anterior. Aquel
 * cambio de layout y de semilla, asi que un anclaje viejo y uno nuevo no son
 * comparables: mezclarlos en la misma tabla obligaria a que cada consulta
 * supiera distinguirlos, y el dia que una se olvidara mostraria un hash que no
 * prueba lo que dice probar.</p>
 *
 * <p>Las entidades de aquel programa —{@code ExpenseContract} y
 * {@code ContractTransaction}— ya no existen en el codigo: nada las escribia, y
 * su unica lectura era una guarda que preguntaba por gastos que aquella tabla
 * ya no podia conocer, asi que respondia que no siempre. Sus tablas
 * siguen en las bases que ya las tienen, con sus registros intactos, porque
 * `ddl-auto=update` no borra nada; lo que se evita es recrearlas vacias en cada
 * base nueva.</p>
 */
@Getter
@Setter
@Entity
public class ExpenseChain extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "expense_id")
    private Expense expense;

    /** La PDA del gasto, derivada de la semilla {@code expense_v2}. */
    @Embedded
    private ContractAddress contractAddress;

    /**
     * Primer eslabon de la cadena, en hexadecimal.
     *
     * <p>Lo calcula el programa al crear el gasto a partir de los terminos con
     * los que se creo. Es el punto de partida para recomponer el encadenado sin
     * leer la cadena entera.</p>
     */
    @Column(length = 64)
    private String genesisHash;

    /**
     * Ultimo encadenado que este backend sabe que quedo escrito en la cadena.
     *
     * <p>Es lo que permite reconciliar con una sola lectura: si el
     * {@code chain_hash} de la cuenta coincide con este valor, la cadena y la
     * base dicen lo mismo y no hay nada que escribir. Si coincide con el
     * siguiente eslabon que ibamos a escribir, la transaccion si entro y lo que
     * se perdio fue el registro local.</p>
     */
    @Column(length = 64)
    private String lastChainHash;

    /** Cuantos movimientos lleva la cadena segun este backend. */
    private Integer recordsCount;

    public ExpenseChain() {}

    public ExpenseChain(ContractAddress contractAddress, Expense expense) {
        this.contractAddress = contractAddress;
        this.expense = expense;
        this.recordsCount = 0;
    }

    public ExpenseChain(ContractAddress contractAddress, Expense expense, String genesisHash) {
        this(contractAddress, expense);
        this.genesisHash = genesisHash;
        this.lastChainHash = genesisHash;
    }
}
