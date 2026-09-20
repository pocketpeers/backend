package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un eslabon de la cadena de un gasto.
 *
 * <p>Hay uno por cada escritura en la cadena: el de la creacion del gasto
 * ({@code payment} nulo) y uno por cada movimiento de pago. Un mismo pago puede
 * tener varios —se registra al crearse, al pagarse y al confirmarse—, porque el
 * programa no guarda estado por pago: corregir un pago es extender el historial,
 * no editarlo.</p>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
public class ExpenseChainRecord extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chain_id")
    private ExpenseChain chain;

    /** Nulo en el eslabon de creacion del gasto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    /**
     * Firma de la transaccion en Solana.
     *
     * <p>Identifica el envio, no el encadenado. Es lo que se enseña en la app y
     * lo que se pega en un explorador de bloques.</p>
     */
    @Embedded
    private TransactionHash transactionHash;

    /**
     * Hash de la linea de este movimiento, en hexadecimal.
     *
     * <p>Lo calcula el programa a partir de los argumentos de la instruccion,
     * asi que cualquiera puede releer la transaccion y recalcularlo sin confiar
     * en este backend. Nulo en el eslabon de creacion.</p>
     */
    @Column(length = 64)
    private String paymentLineHash;

    /**
     * Encadenado resultante despues de aplicar este movimiento, en hexadecimal.
     *
     * <p>Solo se escribe cuando la transaccion quedo confirmada. Guardarlo antes
     * dejaria la base por delante de la cadena, y a partir de ahi nada volveria
     * a cuadrar.</p>
     */
    @Column(length = 64)
    private String chainHash;

    /**
     * Posicion de este eslabon en la cadena, tal como la asigno el programa.
     *
     * <p>El orden real no se puede deducir de {@code createdAt}: los manejadores
     * son asincronos y hay reintentos, asi que dos movimientos pueden quedar
     * guardados en un orden distinto al que entraron en la cadena. Este numero
     * viene de {@code records_count} y es el bueno.</p>
     */
    private Integer recordIndex;
}
