package com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.users.domain.model.entities.IdentityLookupAttempt;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityLookupOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IdentityLookupAttemptRepository extends JpaRepository<IdentityLookupAttempt, Long> {

    /** Intentos de un dispositivo que terminaron en un resultado dado, desde un instante. */
    long countByDeviceHashAndOutcomeAndAttemptedAtAfter(String deviceHash, IdentityLookupOutcome outcome,
                                                         LocalDateTime since);

    /**
     * Cuantos DNI distintos hizo consultar un dispositivo a la fuente externa.
     *
     * <p>Se cuentan documentos distintos y no consultas: si la cache se perdio
     * por un reinicio y el mismo DNI se consulta otra vez, no es un documento
     * nuevo y no debe restarle un intento a la persona.</p>
     */
    @Query("""
    SELECT COUNT(DISTINCT a.dniHash) FROM IdentityLookupAttempt a
    WHERE a.deviceHash = :deviceHash
      AND a.remoteCall = true
      AND a.attemptedAt > :since
""")
    long countDistinctRemoteDnisByDevice(@Param("deviceHash") String deviceHash,
                                         @Param("since") LocalDateTime since);

    /** Si el dispositivo ya consulto ese mismo DNI en la ventana. */
    boolean existsByDeviceHashAndDniHashAndRemoteCallTrueAndAttemptedAtAfter(String deviceHash, String dniHash,
                                                                            LocalDateTime since);

    /** Consultas a la fuente externa salidas desde una IP. */
    long countByIpHashAndRemoteCallTrueAndAttemptedAtAfter(String ipHash, LocalDateTime since);

    /** Consultas a la fuente externa en total, de cualquier origen. */
    long countByRemoteCallTrueAndAttemptedAtAfter(LocalDateTime since);
}
