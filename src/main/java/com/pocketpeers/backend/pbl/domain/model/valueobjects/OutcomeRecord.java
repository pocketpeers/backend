package com.pocketpeers.backend.pbl.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entrada del calculador: el desenlace de una obligacion, ya desligado de JPA.
 *
 * <p>Deliberadamente NO es la entidad {@code ReputationEvent}. El calculador
 * recorre miles de estos registros y la entidad tiene relaciones perezosas hacia
 * {@code User}; recorrerla directamente dispararia una consulta por evento.
 * Proyectar a este record obliga a que el servicio traiga los datos que necesita
 * de una sola vez.</p>
 *
 * <p>La segunda razon es la validacion: los experimentos construyen cohortes
 * sinteticas de cientos de miles de registros, y hacerlo con entidades JPA
 * exigiria una base de datos.</p>
 *
 * @param counterpartyId con quien se contrajo la obligacion (el acreedor)
 * @param groupId        grupo donde ocurrio, para tomar su mediana de montos
 * @param amount         monto de la obligacion
 * @param outcome        como termino
 * @param resolvedAt     cuando quedo resuelta, para calcular su vigencia
 */
public record OutcomeRecord(
        Long counterpartyId,
        Long groupId,
        BigDecimal amount,
        PaymentOutcome outcome,
        LocalDateTime resolvedAt
) {

    public OutcomeRecord {
        if (counterpartyId == null) {
            throw new IllegalArgumentException("counterpartyId es obligatorio");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount debe ser positivo");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome es obligatorio");
        }
        if (resolvedAt == null) {
            throw new IllegalArgumentException("resolvedAt es obligatorio");
        }
    }
}
