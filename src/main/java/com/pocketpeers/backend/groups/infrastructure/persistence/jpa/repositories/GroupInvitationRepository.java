package com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.groups.domain.model.entities.GroupInvitation;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    List<GroupInvitation> findAllByInvitedUser_IdAndStatusAndExpiresAtAfterOrderByIdDesc(
            Long invitedUserId, GroupInvitationStatus status, LocalDateTime now);

    List<GroupInvitation> findAllByGroup_IdAndStatusAndExpiresAtAfterOrderByIdDesc(
            Long groupId, GroupInvitationStatus status, LocalDateTime now);

    boolean existsByGroup_IdAndInvitedUser_IdAndStatusAndExpiresAtAfter(
            Long groupId, Long invitedUserId, GroupInvitationStatus status, LocalDateTime now);

    long countByGroup_IdAndStatusAndExpiresAtAfter(
            Long groupId, GroupInvitationStatus status, LocalDateTime now);

    /** El ultimo rechazo de esa persona a ese grupo, para no insistirle enseguida. */
    Optional<GroupInvitation> findFirstByGroup_IdAndInvitedUser_IdAndStatusOrderByRespondedAtDesc(
            Long groupId, Long invitedUserId, GroupInvitationStatus status);

    /** Al borrar el grupo: sin esto la clave foranea impide borrarlo. */
    @Modifying
    @Query("delete from GroupInvitation i where i.group.id = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);
}
