package com.pocketpeers.backend.users.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDateTime;

/**
 * Como quedo verificada la identidad de una cuenta, y cuando.
 *
 * <p>Solo se guarda el resultado de la comparacion, nunca el nombre que devolvio
 * el servicio externo: conservarlo seria duplicar un dato personal que no hace
 * falta para acreditar nada.</p>
 *
 * <p>Ambos campos admiten nulo: las cuentas creadas antes de esta verificacion
 * no la tienen, y se leen como no verificadas.</p>
 */
@Embeddable
public record IdentityVerification(
        @Enumerated(EnumType.STRING)
        @Column(name = "identity_verification_status", length = 20)
        IdentityVerificationStatus status,

        @Column(name = "identity_verified_at")
        LocalDateTime verifiedAt
) {
    public static IdentityVerification of(IdentityVerificationStatus status, LocalDateTime at) {
        return new IdentityVerification(status, at);
    }
}
