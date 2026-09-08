package com.pocketpeers.backend.pbl.domain.model.aggregates;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
public class UserReputation extends AuditableAbstractAggregateRoot<UserReputation> {
    // One aggregate per user keeps the current PBL state fast to read, while
    // ReputationEvent stores the detailed history of every score change.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int onTimePaymentStreak;

    @Column(nullable = false)
    private int completedPayments;

    // Resultado de PeerScore, que convive con el contador anterior en vez de
    // reemplazarlo. Mantener los dos poblados a la vez es lo que hace que
    // activar o desactivar el motor nuevo sea reversible sin perder datos: el
    // `score` de arriba sigue siendo el del contador y estos campos el del
    // calculo bayesiano.
    //
    // Es tambien la razon por la que el score nuevo vive en su propia columna
    // decimal en lugar de convertir la existente. Con `ddl-auto=update`
    // Hibernate agrega columnas pero nunca cambia el tipo de una que ya existe,
    // asi que reutilizar `score` habria truncado el decimal en silencio.
    //
    // Nulos mientras un usuario no tenga ningun recalculo encima.
    private Double peerScore;

    private Double bandLow;

    private Double bandHigh;

    private Double effectiveCounterparties;

    private Integer distinctCounterparties;

    // Alpha y beta se persisten para poder auditar de donde salio un score sin
    // volver a recorrer el historial.
    private Double alpha;

    private Double beta;

    @Enumerated(EnumType.STRING)
    private ReputationLevel peerLevel;

    private LocalDateTime computedAt;

    // Permite correr dos versiones del algoritmo en paralelo y compararlas.
    private String algoVersion;

    public UserReputation() {
    }

    public UserReputation(User user) {
        this.user = user;
        this.score = 0;
        this.onTimePaymentStreak = 0;
        this.completedPayments = 0;
    }

    public int applyDelta(int delta) {
        // The score is bounded for the product-level reputation meter: events
        // can move it up or down, but the public score stays between 0 and 100.
        this.score = Math.max(0, Math.min(100, this.score + delta));
        return this.score;
    }

    public void registerOnTimePayment() {
        // On-time payments increase both the completed-payment counter and the
        // streak used by several badge unlock rules.
        this.onTimePaymentStreak++;
        this.completedPayments++;
    }

    public void registerPartialPayment() {
        // Partial payments currently affect points through the event delta, but
        // do not count as completed payments or increase the on-time streak.
    }

    public void registerOverduePayment() {
        // An overdue event breaks punctuality streaks even if the user later
        // completes the payment and receives a late-payment event.
        this.onTimePaymentStreak = 0;
    }

    public void registerLatePayment() {
        this.completedPayments++;
    }

    /**
     * Guarda el resultado de un recalculo de PeerScore.
     *
     * <p>No acumula nada: sobrescribe. El score nuevo se recalcula completo
     * desde el historial, asi que el valor anterior no aporta informacion.</p>
     */
    public void applyPeerScore(ScoreResult result, String algoVersion, LocalDateTime computedAt) {
        this.peerScore = result.score();
        this.bandLow = result.bandLow();
        this.bandHigh = result.bandHigh();
        this.effectiveCounterparties = result.effectiveCounterparties();
        this.distinctCounterparties = result.distinctCounterparties();
        this.alpha = result.alpha();
        this.beta = result.beta();
        this.peerLevel = result.level();
        this.algoVersion = algoVersion;
        this.computedAt = computedAt;
    }

    /**
     * Si este usuario ya tiene un recalculo encima.
     *
     * <p>Comprueba el conjunto y no solo el score porque {@link #applyPeerScore}
     * escribe los cinco campos juntos: si alguno falta, lo que hay es una fila
     * anterior al motor nuevo y no un resultado parcial que se pueda mostrar.</p>
     */
    public boolean hasPeerScore() {
        return peerScore != null && peerLevel != null && bandLow != null && bandHigh != null
                && effectiveCounterparties != null;
    }


    public ReputationLevel getLevel() {
        return ReputationLevel.fromScore(score);
    }

    public int getPointsToNextLevel() {
        return ReputationLevel.pointsToNextLevel(score);
    }
}
