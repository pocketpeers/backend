package com.pocketpeers.backend.users.domain.model.entities;

import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Codigo de un solo uso para restablecer una contrasena olvidada.
 *
 * <p>El codigo nunca se guarda en claro: se almacena su hash, igual que una
 * contrasena. Quien consiga leer esta tabla no puede usar los codigos que
 * contiene.</p>
 *
 * <p>Tres defensas conviven aqui, porque un codigo de seis digitos es corto y por
 * si solo seria adivinable: vence pronto, sirve una sola vez, y admite un numero
 * limitado de intentos antes de invalidarse.</p>
 */
@Getter
@Entity
public class PasswordResetCode extends AuditableModel {

    /** Minutos de vigencia. Corto a proposito: un codigo de 6 digitos vive poco. */
    public static final int EXPIRATION_MINUTES = 15;

    /** Intentos fallidos tolerados antes de invalidar el codigo. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Hash del codigo, nunca el codigo en claro. */
    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private int attempts;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(Long userId, String codeHash, LocalDateTime issuedAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = issuedAt.plusMinutes(EXPIRATION_MINUTES);
        this.attempts = 0;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean hasAttemptsLeft() {
        return attempts < MAX_ATTEMPTS;
    }

    /** Un codigo solo sirve si no vencio, no se uso y le quedan intentos. */
    public boolean isUsable(LocalDateTime now) {
        return !isExpired(now) && !isUsed() && hasAttemptsLeft();
    }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }
}
