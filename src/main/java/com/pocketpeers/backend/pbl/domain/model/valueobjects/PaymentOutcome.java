package com.pocketpeers.backend.pbl.domain.model.valueobjects;

/**
 * Resultado observado de una obligacion, con el valor que aporta al score.
 *
 * <p>El valor esta en [0, 1] y representa "que tan cumplida" quedo la obligacion.
 * No son puntos: es la fraccion de exito que ese evento aporta a la estimacion.
 * Un pago puntual aporta evidencia completa de cumplimiento; uno vencido aporta
 * evidencia completa de incumplimiento.</p>
 *
 * <p>Los valores intermedios reconocen que un abono parcial dentro del plazo
 * demuestra mas compromiso que un pago completo fuera de plazo, aunque ninguno
 * de los dos sea un cumplimiento limpio.</p>
 */
public enum PaymentOutcome {
    PUNTUAL(1.0),
    PARCIAL_A_TIEMPO(0.6),
    TARDIO(0.3),
    VENCIDO(0.0);

    private final double value;

    PaymentOutcome(double value) {
        this.value = value;
    }

    /** Valor v del evento, en [0, 1]. */
    public double value() {
        return value;
    }

    /**
     * Traduce los tipos del motor PBL vigente al resultado que consume PeerScore.
     *
     * <p>Los tipos que no representan el desenlace de una obligacion
     * (EARLY_PAYMENT, JUST_IN_TIME_PAYMENT, GROUP_CREATED, ZERO_DEBT,
     * MANUAL_ADJUSTMENT) existen solo para desbloquear insignias y no producen
     * evidencia de reputacion, por eso devuelven null.</p>
     */
    public static PaymentOutcome fromEventType(ReputationEventType type) {
        return switch (type) {
            case ON_TIME_PAYMENT -> PUNTUAL;
            case PARTIAL_PAYMENT -> PARCIAL_A_TIEMPO;
            case LATE_PAYMENT -> TARDIO;
            case OVERDUE_PAYMENT -> VENCIDO;
            default -> null;
        };
    }
}
