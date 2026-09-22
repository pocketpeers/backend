package com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.users.domain.model.entities.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /**
     * Ultima solicitud emitida para un correo.
     *
     * <p>Solo la mas reciente vale: pedir un codigo nuevo deja sin efecto al
     * anterior, para que no queden varios vivos a la vez.</p>
     */
    Optional<PendingRegistration> findFirstByEmailOrderByIdDesc(String email);

    /**
     * Hay un alta pendiente <b>de otra persona</b> que todavia puede
     * completarse con ese nombre de usuario.
     *
     * <p>El {@code p.email <> :email} es lo importante, y faltaba. La reserva
     * existe para que dos personas distintas no pidan el codigo con el mismo
     * nombre y la segunda en confirmar se estrelle despues de haberlo hecho
     * todo bien. Sin excluir el correo propio, quien volvia atras y reintentaba
     * su propio registro chocaba con su solicitud anterior: la aplicacion le
     * decia que el usuario ya estaba tomado —tomado por el mismo— y en la base
     * de datos no habia ninguna cuenta que lo explicara.</p>
     *
     * <p>Excluirlo es seguro porque el reintento invalida su pendiente anterior
     * acto seguido, con {@link #invalidatePendingFor}.</p>
     */
    @Query("""
    SELECT COUNT(p) > 0 FROM PendingRegistration p
    WHERE p.username = :username
      AND p.email <> :email
      AND p.usedAt IS NULL
      AND p.expiresAt > :now
""")
    boolean existsUsablePendingForOtherEmail(@Param("username") String username,
                                             @Param("email") String email,
                                             @Param("now") LocalDateTime now);

    /** Invalida las solicitudes pendientes de un correo. */
    @Modifying
    @Query("update PendingRegistration p set p.usedAt = :now where p.email = :email and p.usedAt is null")
    void invalidatePendingFor(@Param("email") String email, @Param("now") LocalDateTime now);
}
