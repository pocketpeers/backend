package com.pocketpeers.backend.groups.interfaces.rest;

import com.pocketpeers.backend.groups.application.internal.invitations.GroupInvitationService;
import com.pocketpeers.backend.groups.domain.model.entities.GroupInvitation;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.interfaces.rest.resources.AcceptInvitationResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupInvitationResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupMemberResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.InvitationCandidateResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.InviteMemberResource;
import com.pocketpeers.backend.groups.interfaces.rest.transform.GroupMemberResourceFromEntityAssembler;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Invitaciones por nombre de usuario.
 *
 * <p>Quien actua sale siempre de la sesion, nunca del cuerpo de la peticion:
 * aceptar en nombre de otro seria meterlo a un grupo sin que lo decida.</p>
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Group Invitations", description = "Invitaciones a un grupo por nombre de usuario")
public class GroupInvitationController {

    private final GroupInvitationService invitationService;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupInvitationController(GroupInvitationService invitationService,
                                     UserRepository userRepository,
                                     GroupMemberRepository groupMemberRepository) {
        this.invitationService = invitationService;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Operation(summary = "Look up a user to invite, to confirm who it is before sending")
    @GetMapping("/groups/{groupId}/invitations/candidate")
    public ResponseEntity<?> candidate(@PathVariable Long groupId,
                                       @RequestParam String username,
                                       Authentication authentication) {
        if (groupAdmin(groupId, authentication).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return invitationService.findCandidate(groupId, username)
                .<ResponseEntity<?>>map(candidate -> ResponseEntity.ok(new InvitationCandidateResource(
                        candidate.user().getId(),
                        candidate.user().getUsername(),
                        candidate.fullName(),
                        candidate.photo(),
                        candidate.availability().name())))
                .orElseGet(GroupInvitationController::userNotFound);
    }

    @Operation(summary = "Invite a user to the group by username")
    @PostMapping("/groups/{groupId}/invitations")
    public ResponseEntity<GroupInvitationResource> invite(@PathVariable Long groupId,
                                                          @RequestBody InviteMemberResource resource,
                                                          Authentication authentication) {
        var admin = groupAdmin(groupId, authentication);
        if (admin.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var invitation = invitationService.invite(groupId, admin.get(), resource.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResource(invitation));
    }

    @Operation(summary = "Pending invitations of a group")
    @GetMapping("/groups/{groupId}/invitations")
    public ResponseEntity<List<GroupInvitationResource>> pendingOfGroup(@PathVariable Long groupId,
                                                                        Authentication authentication) {
        if (groupAdmin(groupId, authentication).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(invitationService.pendingOfGroup(groupId).stream()
                .map(this::toResource)
                .toList());
    }

    @Operation(summary = "Cancel a pending invitation")
    @DeleteMapping("/groups/{groupId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancel(@PathVariable Long groupId,
                                       @PathVariable Long invitationId,
                                       Authentication authentication) {
        if (groupAdmin(groupId, authentication).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        invitationService.cancel(groupId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Pending invitations of the authenticated user")
    @GetMapping("/invitations/me")
    public ResponseEntity<List<GroupInvitationResource>> mine(Authentication authentication) {
        var user = currentUser(authentication);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(invitationService.pendingFor(user.get().getId()).stream()
                .map(this::toResource)
                .toList());
    }

    @Operation(summary = "Accept an invitation, signing the membership declaration")
    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<GroupMemberResource> accept(@PathVariable Long invitationId,
                                                      @RequestBody AcceptInvitationResource resource,
                                                      Authentication authentication) {
        var user = currentUser(authentication);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var member = invitationService.accept(invitationId, user.get(),
                resource.acceptedDeclarationVersion(), resource.signatureImage());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GroupMemberResourceFromEntityAssembler.fromEntityToResource(member));
    }

    @Operation(summary = "Reject an invitation")
    @PostMapping("/invitations/{invitationId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long invitationId, Authentication authentication) {
        var user = currentUser(authentication);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        invitationService.reject(invitationId, user.get());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(GroupInvitationService.InvitedUserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(GroupInvitationService.InvitedUserNotFoundException ex) {
        return userNotFound();
    }

    private static ResponseEntity<Map<String, String>> userNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", "No se encontro el usuario"));
    }

    private GroupInvitationResource toResource(GroupInvitation invitation) {
        var group = invitation.getGroup();
        var invited = invitation.getInvitedUser();
        var invitedBy = invitation.getInvitedBy();
        return new GroupInvitationResource(
                invitation.getId(),
                group.getId(),
                group.getName(),
                group.getGroupPhoto(),
                invited.getId(),
                invited.getUsername(),
                invitationService.fullNameOf(invited),
                invitationService.photoOf(invited),
                invitedBy.getUsername(),
                invitationService.fullNameOf(invitedBy),
                invitation.getStatus().name(),
                invitation.getExpiresAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    private Optional<User> currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName());
    }

    /** El usuario de la sesion, solo si administra ESE grupo. */
    private Optional<User> groupAdmin(Long groupId, Authentication authentication) {
        return currentUser(authentication)
                .filter(user -> groupMemberRepository.existsByGroupIdAndUser_IdAndRole(
                        groupId, user.getId(), GroupRole.ADMIN));
    }
}
