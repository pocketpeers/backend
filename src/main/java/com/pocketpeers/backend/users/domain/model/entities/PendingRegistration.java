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
 * Un registro a la espera de que su dueno confirme el correo.
 *
 * <p>Guarda los datos del alta hasta que llega el codigo correcto. Solo
 * entonces se crea el usuario. El orden importa: si la cuenta se creara antes,
 * un correo mal escrito —o de otra persona— dejaria una cuenta activa que nadie
 * puede recuperar, porque la recuperacion de contrasena se apoya justamente en
 * ese correo.</p>
 *
 * <p><b>La contrasena se guarda ya cifrada</b>, con el mismo algoritmo que la de
 * un usuario real. Esta tabla es temporal, pero mientras existe contiene
 * credenciales de gente que todavia no tiene cuenta, y no hay ninguna razon
 * para que esten en claro ni un minuto.</p>
 *
 * <p>Las tres defensas del codigo son las mismas que en la recuperacion de
 * contrasena, y por el mismo motivo: seis digitos son pocos. Vence pronto,
 * sirve una sola vez y tolera un numero limitado de intentos.</p>
 */
@Getter
@Entity
public class PendingRegistration extends AuditableModel {

    /** Minutos de vigencia del codigo. */
    public static final int EXPIRATION_MINUTES = 15;

    /** Intentos fallidos tolerados antes de invalidar el codigo. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    /** Cifrada, nunca en claro. */
    @Column(nullable = false)
    private String passwordHash;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String photo;
    private String documentType;
    private String documentNumber;

    /** Hash del codigo, nunca el codigo. */
    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private int attempts;

    protected PendingRegistration() {
    }

    public PendingRegistration(String email,
                               String username,
                               String passwordHash,
                               String firstName,
                               String lastName,
                               String phoneNumber,
                               String photo,
                               String documentType,
                               String documentNumber,
                               String codeHash,
                               LocalDateTime issuedAt) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.photo = photo;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.codeHash = codeHash;
        this.expiresAt = issuedAt.plusMinutes(EXPIRATION_MINUTES);
        this.attempts = 0;
    }

    /** Sigue sirviendo: ni usado, ni vencido, ni agotado a base de intentos. */
    public boolean isUsable(LocalDateTime now) {
        return usedAt == null
                && attempts < MAX_ATTEMPTS
                && now.isBefore(expiresAt);
    }

    public void registerFailedAttempt() {
        attempts++;
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }
}
