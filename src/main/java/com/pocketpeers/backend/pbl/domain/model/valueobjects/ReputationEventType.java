package com.pocketpeers.backend.pbl.domain.model.valueobjects;

public enum ReputationEventType {
    ON_TIME_PAYMENT,
    PARTIAL_PAYMENT,
    OVERDUE_PAYMENT,
    LATE_PAYMENT,
    MANUAL_ADJUSTMENT,
    EARLY_PAYMENT,
    GROUP_CREATED,
    JUST_IN_TIME_PAYMENT,
    ZERO_DEBT,

    // Los dos siguientes no describen el desenlace de una obligacion: los emite
    // el modulo de operaciones para dejar constancia de que un gasto vino con
    // comprobante y de que un pago fue confirmado por quien recibio el dinero.
    // Aportan cero puntos y PaymentOutcome.fromEventType los devuelve como null,
    // asi que no son evidencia de reputacion: solo desbloquean insignias.
    RECEIPT_ATTACHED,
    PAYMENT_CONFIRMED
}
