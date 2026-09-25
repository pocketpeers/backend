package com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMembershipDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMembershipDeclarationRepository extends JpaRepository<GroupMembershipDeclaration, Long> {

    Optional<GroupMembershipDeclaration> findTopByOrderByIdDesc();

    List<GroupMembershipDeclaration> findAllByOrderByIdAsc();

    List<GroupMembershipDeclaration> findAllByUserIdOrderByIdAsc(Long userId);

    List<GroupMembershipDeclaration> findAllByGroupIdOrderByIdAsc(Long groupId);

    /**
     * Serializa las firmas hasta que termine la transaccion.
     *
     * <p>Un {@code SELECT ... FOR UPDATE} sobre el ultimo eslabon no alcanza: la
     * firma que espera no ve la fila que la primera inserto, y al despertar lee
     * el mismo "ultimo" de antes. Con el candado consultivo, la segunda
     * transaccion no lee nada hasta que la primera confirma, y entonces su
     * consulta ya ve la fila nueva. Se libera solo al hacer commit o rollback.</p>
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS lock", nativeQuery = true)
    Integer acquireChainLock(@Param("key") long key);
}
