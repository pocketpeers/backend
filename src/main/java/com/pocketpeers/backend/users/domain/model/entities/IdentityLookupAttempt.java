package com.pocketpeers.backend.users.domain.model.entities;

import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityLookupOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Un intento de verificar un DNI durante el registro.
 *
 * <p>Sirve para dos cosas. La primera, aplicar los topes que protegen la cuota
 * mensual del servicio externo: cuantos DNI distintos probo un dispositivo,
 * cuantas veces fallo, cuantas consultas salieron desde una IP y cuantas en
 * total. Por eso vive en la base y no en memoria: un reinicio del servidor no
 * debe devolverle los intentos a nadie.</p>
 *
 * <p>La segunda, dejar evidencia para el informe de validacion: cuantas
 * verificaciones hubo, cuantas coincidieron y si hubo intentos de abuso.</p>
 *
 * <p><b>El dispositivo, la IP y el DNI se guardan como hash con secreto</b>,
 * nunca en claro. Alcanza para contar y comparar, y evita almacenar el
 * documento de gente que nunca llego a registrarse.</p>
 */
@Getter
@Entity
@Table(indexes = {
        @Index(name = "ix_identity_lookup_attempts_device", columnList = "device_hash, attempted_at"),
        @Index(name = "ix_identity_lookup_attempts_ip", columnList = "ip_hash, attempted_at"),
        @Index(name = "ix_identity_lookup_attempts_time", columnList = "attempted_at")
})
public class IdentityLookupAttempt extends AuditableModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(name = "dni_hash", nullable = false, length = 64)
    private String dniHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityLookupOutcome outcome;

    /**
     * Si este intento gasto una consulta de la cuota. Es falso cuando la
     * respuesta salio de la cache, o cuando no se llego a consultar.
     */
    @Column(name = "remote_call", nullable = false)
    private boolean remoteCall;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    protected IdentityLookupAttempt() {
    }

    public IdentityLookupAttempt(String deviceHash, String ipHash, String dniHash,
                                 IdentityLookupOutcome outcome, boolean remoteCall, LocalDateTime attemptedAt) {
        this.deviceHash = deviceHash;
        this.ipHash = ipHash;
        this.dniHash = dniHash;
        this.outcome = outcome;
        this.remoteCall = remoteCall;
        this.attemptedAt = attemptedAt;
    }
}
