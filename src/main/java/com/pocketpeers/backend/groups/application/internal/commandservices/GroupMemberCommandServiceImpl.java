package com.pocketpeers.backend.groups.application.internal.commandservices;

import com.pocketpeers.backend.groups.application.internal.declarations.MembershipDeclarationService;
import com.pocketpeers.backend.groups.domain.exceptions.GroupNotFoundException;
import com.pocketpeers.backend.groups.domain.model.commands.AddMemberCommand;
import com.pocketpeers.backend.groups.domain.model.commands.JoinGroupWithTokenCommand;
import com.pocketpeers.backend.groups.domain.model.commands.RemoveMemberCommand;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.domain.services.GroupMemberCommandService;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.operations.domain.exceptions.UserNotFoundException;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GroupMemberCommandServiceImpl implements GroupMemberCommandService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final MembershipDeclarationService membershipDeclarationService;

    public GroupMemberCommandServiceImpl(GroupMemberRepository groupMemberRepository, GroupRepository groupRepository, UserRepository userRepository,
                                         MembershipDeclarationService membershipDeclarationService) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.membershipDeclarationService = membershipDeclarationService;
    }


    @Override
    public Optional<GroupMember> handle(AddMemberCommand command) {


        var group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new GroupNotFoundException(command.groupId()));

        var user = userRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        if (groupMemberRepository.existsGroupMemberByGroupAndUser(group, user)) {
            throw new IllegalArgumentException("User is already a member of the group");
        }

        var member = new GroupMember(group, user, GroupRole.MEMBER);

        try {
            return Optional.of(groupMemberRepository.save(member));
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while adding member to the group: " + e.getMessage());
        }
    }

    @Override
    public void handle(RemoveMemberCommand command) {
        var group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new GroupNotFoundException(command.groupId()));


        var memberToRemove = groupMemberRepository.findByGroupIdAndUser_Id(group.getId(), command.userId())
                .orElseThrow(() -> new RuntimeException("Member not found in group"));

        // Al administrador no se le saca del grupo. No es una regla de permisos
        // sino una invariante: el grupo resuelve quien lo administra buscando al
        // miembro con ese rol, y si no encuentra ninguno devuelve null. Un grupo
        // sin administrador queda inservible y sin salida, porque editarlo,
        // invitar, ver morosos y hasta eliminarlo exigen ser el administrador
        // que ya no existe. Como no hay forma de transferir el rol, la unica
        // defensa es impedir que se quede vacio.
        if (memberToRemove.isAdmin()) {
            throw new IllegalArgumentException("No se puede quitar al administrador del grupo");
        }

        try {
            groupMemberRepository.delete(memberToRemove);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while deleting member from the group: " + e.getMessage());
        }
    }

    /**
     * Entrar con codigo exige firmar la declaracion jurada. Firma e ingreso van
     * en la misma transaccion: no queda una sin la otra.
     */
    @Override
    @Transactional
    public Optional<GroupMember> handle(JoinGroupWithTokenCommand command) {
        // Varios grupos sin codigo lo tienen vacio; buscar por "" encontraria
        // mas de uno y reventaria en vez de rechazar el codigo.
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("Invalid invitation token");
        }
        var group = command.groupId() == null
                ? groupRepository.findByInvitationToken(command.token())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"))
                : groupRepository.findById(command.groupId())
                .orElseThrow(() -> new GroupNotFoundException(command.groupId()));

        if (!group.hasValidInvitationToken(command.token())) {
            throw new IllegalArgumentException("Invalid invitation token");
        }

        var user = userRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        if (groupMemberRepository.existsGroupMemberByGroupAndUser(group, user)) {
            throw new IllegalArgumentException("User is already a member of the group");
        }

        membershipDeclarationService.sign(user.getId(), group.getId(), group.getName(),
                command.acceptedDeclarationVersion(), command.signatureImage());

        var newMember = new GroupMember(group, user, GroupRole.MEMBER);
        return Optional.of(groupMemberRepository.save(newMember));
    }





}
