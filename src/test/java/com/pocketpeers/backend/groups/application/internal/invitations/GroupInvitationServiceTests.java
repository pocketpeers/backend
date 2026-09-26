package com.pocketpeers.backend.groups.application.internal.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pocketpeers.backend.groups.application.internal.declarations.MembershipDeclarationService;
import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.entities.GroupInvitation;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupInvitationStatus;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupInvitationRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.operations.infrastructure.notifications.FcmNotificationService;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTests {

    private static final ZoneId ZONE = ZoneId.of("America/Lima");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 10, 0);

    @Mock private GroupInvitationRepository invitationRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserInformationRepository userInformationRepository;
    @Mock private MembershipDeclarationService membershipDeclarationService;
    @Mock private FcmNotificationService fcmNotificationService;

    private GroupInvitationService service;
    private Group group;
    private User admin;
    private User ana;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new GroupInvitationService(invitationRepository, groupRepository, groupMemberRepository,
                userRepository, userInformationRepository, membershipDeclarationService,
                fcmNotificationService, clock);
        group = withId(new Group("Depa", "", ""), 10L);
        admin = withId(new User("admin", "x"), 1L);
        ana = withId(new User("mialaos", "x"), 2L);
        lenient().when(userInformationRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        lenient().when(invitationRepository.save(any(GroupInvitation.class)))
                .thenAnswer(call -> withId(call.getArgument(0), 99L));
    }

    @Test
    void candidateLookupAcceptsTheAtSignAndIgnoresCaseWhenThereIsOneMatch() {
        when(userRepository.findByUsername("MiaLaos")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("MiaLaos")).thenReturn(List.of(ana));

        var candidate = service.findCandidate(10L, "  @MiaLaos ");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().user()).isSameAs(ana);
        assertThat(candidate.get().availability()).isEqualTo(GroupInvitationService.Availability.AVAILABLE);
    }

    @Test
    void candidateLookupDoesNotGuessBetweenUsernamesThatOnlyDifferInCase() {
        var other = withId(new User("MIALAOS", "x"), 3L);
        when(userRepository.findByUsername("Mialaos")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("Mialaos")).thenReturn(List.of(ana, other));

        assertThat(service.findCandidate(10L, "Mialaos")).isEmpty();
    }

    @Test
    void unknownUserIsNotFound() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("nadie")).thenReturn(List.of());

        assertThatThrownBy(() -> service.invite(10L, admin, "nadie"))
                .isInstanceOf(GroupInvitationService.InvitedUserNotFoundException.class)
                .hasMessage("No se encontro el usuario");
    }

    @Test
    void inviteCreatesAPendingInvitationThatExpiresInSevenDaysAndPushesIt() {
        givenInvitable();

        var invitation = service.invite(10L, admin, "mialaos");

        assertThat(invitation.getStatus()).isEqualTo(GroupInvitationStatus.PENDING);
        assertThat(invitation.getInvitedUser()).isSameAs(ana);
        assertThat(invitation.getInvitedBy()).isSameAs(admin);
        assertThat(invitation.getExpiresAt()).isEqualTo(NOW.plusDays(7));
        verify(fcmNotificationService).sendToUser(eq(2L), anyString(), anyString(), anyMap());
    }

    @Test
    void aFailingPushDoesNotUndoTheInvitation() {
        givenInvitable();
        when(fcmNotificationService.sendToUser(anyLong(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("firebase caido"));

        assertThat(service.invite(10L, admin, "mialaos").getStatus()).isEqualTo(GroupInvitationStatus.PENDING);
    }

    @Test
    void cannotInviteSomeoneWhoIsAlreadyAMember() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByUsername("mialaos")).thenReturn(Optional.of(ana));
        when(groupMemberRepository.findByGroupIdAndUser_Id(10L, 2L))
                .thenReturn(Optional.of(new GroupMember(group, ana, GroupRole.MEMBER)));

        assertThatThrownBy(() -> service.invite(10L, admin, "mialaos"))
                .hasMessage("Esa persona ya es integrante del grupo");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void cannotInviteTwiceWhileTheFirstIsPending() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByUsername("mialaos")).thenReturn(Optional.of(ana));
        when(invitationRepository.existsByGroup_IdAndInvitedUser_IdAndStatusAndExpiresAtAfter(
                10L, 2L, GroupInvitationStatus.PENDING, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.invite(10L, admin, "mialaos"))
                .hasMessage("Esa persona ya tiene una invitacion pendiente");
    }

    @Test
    void aRejectionBlocksReinvitingForThreeDays() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByUsername("mialaos")).thenReturn(Optional.of(ana));
        when(invitationRepository.findFirstByGroup_IdAndInvitedUser_IdAndStatusOrderByRespondedAtDesc(
                10L, 2L, GroupInvitationStatus.REJECTED))
                .thenReturn(Optional.of(rejectedAt(NOW.minusDays(2))));

        assertThatThrownBy(() -> service.invite(10L, admin, "mialaos"))
                .hasMessageContaining("rechazo la invitacion hace poco");
    }

    @Test
    void anOldRejectionAllowsInvitingAgain() {
        givenInvitable();
        when(invitationRepository.findFirstByGroup_IdAndInvitedUser_IdAndStatusOrderByRespondedAtDesc(
                10L, 2L, GroupInvitationStatus.REJECTED))
                .thenReturn(Optional.of(rejectedAt(NOW.minusDays(4))));

        assertThat(service.invite(10L, admin, "mialaos").getStatus()).isEqualTo(GroupInvitationStatus.PENDING);
    }

    @Test
    void theGroupCannotPileUpPendingInvitations() {
        givenInvitable();
        when(invitationRepository.countByGroup_IdAndStatusAndExpiresAtAfter(
                10L, GroupInvitationStatus.PENDING, NOW)).thenReturn(20L);

        assertThatThrownBy(() -> service.invite(10L, admin, "mialaos"))
                .hasMessageContaining("20 invitaciones pendientes");
    }

    @Test
    void acceptingSignsTheDeclarationAndJoinsTheGroup() {
        var invitation = pendingInvitation();
        when(invitationRepository.findById(99L)).thenReturn(Optional.of(invitation));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(call -> call.getArgument(0));

        var member = service.accept(99L, ana, "v1", "firma");

        assertThat(member.getUser()).isSameAs(ana);
        assertThat(member.getRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(invitation.getStatus()).isEqualTo(GroupInvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
        verify(membershipDeclarationService).sign(2L, 10L, "Depa", "v1", "firma");
        verify(fcmNotificationService).sendToUser(eq(1L), anyString(), anyString(), anyMap());
    }

    @Test
    void onlyTheInvitedPersonCanAnswer() {
        when(invitationRepository.findById(99L)).thenReturn(Optional.of(pendingInvitation()));
        var intruder = withId(new User("otro", "x"), 7L);

        assertThatThrownBy(() -> service.accept(99L, intruder, "v1", "firma"))
                .hasMessage("La invitacion no existe");
        assertThatThrownBy(() -> service.reject(99L, intruder))
                .hasMessage("La invitacion no existe");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void anExpiredInvitationCannotBeAccepted() {
        var invitation = withId(new GroupInvitation(group, ana, admin, NOW.minusMinutes(1)), 99L);
        when(invitationRepository.findById(99L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept(99L, ana, "v1", "firma"))
                .hasMessage("La invitacion ya no esta vigente");
        verify(membershipDeclarationService, never()).sign(any(), any(), any(), any(), any());
    }

    @Test
    void rejectingClosesTheInvitation() {
        var invitation = pendingInvitation();
        when(invitationRepository.findById(99L)).thenReturn(Optional.of(invitation));

        service.reject(99L, ana);

        assertThat(invitation.getStatus()).isEqualTo(GroupInvitationStatus.REJECTED);
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
    }

    @Test
    void pendingListDropsInvitationsToGroupsThePersonAlreadyJoined() {
        var invitation = pendingInvitation();
        when(invitationRepository.findAllByInvitedUser_IdAndStatusAndExpiresAtAfterOrderByIdDesc(
                2L, GroupInvitationStatus.PENDING, NOW)).thenReturn(List.of(invitation));
        when(groupMemberRepository.findByGroupIdAndUser_Id(10L, 2L))
                .thenReturn(Optional.of(new GroupMember(group, ana, GroupRole.MEMBER)));

        assertThat(service.pendingFor(2L)).isEmpty();
        assertThat(invitation.getStatus()).isEqualTo(GroupInvitationStatus.CANCELLED);
    }

    private void givenInvitable() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByUsername("mialaos")).thenReturn(Optional.of(ana));
    }

    private GroupInvitation pendingInvitation() {
        return withId(new GroupInvitation(group, ana, admin, NOW.plusDays(7)), 99L);
    }

    private GroupInvitation rejectedAt(LocalDateTime when) {
        var invitation = new GroupInvitation(group, ana, admin, when.plusDays(7));
        invitation.reject(when);
        return invitation;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
