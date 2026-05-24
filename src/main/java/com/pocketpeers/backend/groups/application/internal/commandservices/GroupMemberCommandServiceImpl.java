package com.pocketpeers.backend.groups.application.internal.commandservices;

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
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GroupMemberCommandServiceImpl implements GroupMemberCommandService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final UserInformationRepository userInformationRepository;

    public GroupMemberCommandServiceImpl(GroupMemberRepository groupMemberRepository, GroupRepository groupRepository, UserInformationRepository userInformationRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.userInformationRepository = userInformationRepository;
    }


    @Override
    public Optional<GroupMember> handle(AddMemberCommand command) {


        var group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new GroupNotFoundException(command.groupId()));

        var user = userInformationRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        if (groupMemberRepository.existsByGroupAndUserInformation(group, user)) {
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


        var memberToRemove = groupMemberRepository.findByGroupIdAndUserInformationId(group.getId(), command.userId())
                .orElseThrow(() -> new RuntimeException("Member not found in group"));

        try {
            groupMemberRepository.delete(memberToRemove);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while deleting member from the group: " + e.getMessage());
        }
    }

    @Override
    public Optional<GroupMember> handle(JoinGroupWithTokenCommand command) {
        var group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new GroupNotFoundException(command.groupId()));

        if (!group.hasValidInvitationToken(command.token())) {
            throw new IllegalArgumentException("Invalid invitation token");
        }

        var user = userInformationRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        if (groupMemberRepository.existsByGroupAndUserInformation(group, user)) {
            throw new IllegalArgumentException("User is already a member of the group");
        }

        var newMember = new GroupMember(group, user, GroupRole.MEMBER);
        return Optional.of(groupMemberRepository.save(newMember));
    }





}
