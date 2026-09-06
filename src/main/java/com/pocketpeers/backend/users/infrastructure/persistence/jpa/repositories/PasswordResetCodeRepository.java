package com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.users.domain.model.entities.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /**
     * Ultimo codigo emitido para un usuario.
     *
     * <p>Solo el mas reciente es valido: pedir un codigo nuevo debe dejar sin
     * efecto al anterior, para que no queden varios vivos a la vez.</p>
     */
    Optional<PasswordResetCode> findFirstByUserIdOrderByIdDesc(Long userId);

    /** Invalida los codigos pendientes de un usuario marcandolos como usados. */
    @Modifying
    @Query("update PasswordResetCode c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
    void invalidatePendingCodes(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
