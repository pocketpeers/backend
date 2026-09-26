package com.pocketpeers.backend.groups.domain.model.entities;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupInvitationStatus;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Invitacion que el administrador manda a alguien por su nombre de usuario.
 *
 * <p>A diferencia del codigo, aqui la persona no entra sola: queda pendiente
 * hasta que acepta, y al aceptar firma la declaracion jurada igual que quien
 * entra con codigo. Entrar a un grupo no es inocuo —se pasa a ver los gastos de
 * los demas y se puede terminar como contraparte de sus obligaciones—, asi que
 * nadie queda dentro sin haberlo decidido.</p>
 */
@Getter
@Entity
@Table(name = "group_invitations", indexes = {
        @Index(name = "ix_group_invitations_invited_status", columnList = "invited_user_id, status"),
        @Index(name = "ix_group_invitations_group_status", columnList = "group_id, status")
})
public class GroupInvitation extends AuditableModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id")
    private User invitedUser;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupInvitationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Cuando la persona acepto o rechazo, o el administrador la cancelo. */
    private LocalDateTime respondedAt;

    protected GroupInvitation() {
    }

    public GroupInvitation(Group group, User invitedUser, User invitedBy, LocalDateTime expiresAt) {
        this.group = group;
        this.invitedUser = invitedUser;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.status = GroupInvitationStatus.PENDING;
    }

    /** Pendiente y todavia sin vencer: la unica que se puede aceptar o rechazar. */
    public boolean isActive(LocalDateTime now) {
        return status == GroupInvitationStatus.PENDING && now.isBefore(expiresAt);
    }

    public void accept(LocalDateTime now) {
        requireActive(now);
        close(GroupInvitationStatus.ACCEPTED, now);
    }

    public void reject(LocalDateTime now) {
        requireActive(now);
        close(GroupInvitationStatus.REJECTED, now);
    }

    public void cancel(LocalDateTime now) {
        requireActive(now);
        close(GroupInvitationStatus.CANCELLED, now);
    }

    private void requireActive(LocalDateTime now) {
        if (!isActive(now)) {
            throw new IllegalArgumentException("La invitacion ya no esta vigente");
        }
    }

    private void close(GroupInvitationStatus newStatus, LocalDateTime now) {
        this.status = newStatus;
        this.respondedAt = now;
    }
}
