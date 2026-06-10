package com.pocketpeers.backend.groups.application.internal.commandservices;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.commands.*;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.domain.services.GroupCommandService;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GroupCommandServiceImpl implements GroupCommandService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupCommandServiceImpl(GroupRepository groupRepository,
                                   GroupMemberRepository groupMemberRepository,
                                   UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }


    @Override
    public Long handle(CreateGroupCommand command) {

        var user = userRepository.findById(command.adminId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (groupRepository.existsByName(command.name())) {
            throw new IllegalArgumentException("Group with same title already exists");
        }

        var group = new Group(command);
        var groupMember = new GroupMember(group, user, GroupRole.ADMIN);

        try {
         groupRepository.save(group);
         groupMemberRepository.save(groupMember);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while saving group: " + e.getMessage());
        }

        return group.getId();
    }

    @Override
    public Optional<Group> handle(UpdateGroupImageCommand command) {

        Optional<Group> groupOptional = groupRepository.findById(command.id());

        if (groupOptional.isEmpty()) {
            return Optional.empty();
        }

        Group group = groupOptional.get();
        group.changePhoto(command.image());
        groupRepository.save(group);
        return Optional.of(group);
    }

    @Override
    public Optional<Group> handle(UpdateGroupCommand command) {

        if (groupRepository.existsByNameAndIdIsNot(command.name(), command.groupId()))
            throw new IllegalArgumentException("Group with same title already exists");

        var result = groupRepository.findById(command.groupId());
        if (result.isEmpty()) throw new IllegalArgumentException("Group does not exist");
        var groupToUpdate = result.get();
        try {
            var updatedGroup = groupRepository.save(groupToUpdate.updateInformation(command.name(), command.description()));
            return Optional.of(updatedGroup);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while updating group: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handle(DeleteGroupCommand command) {
        if (!groupRepository.existsById(command.groupId())) {
            throw new IllegalArgumentException("Group does not exist");
        }
        try {
            groupMemberRepository.deleteByGroupId(command.groupId());
            groupRepository.deleteById(command.groupId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while deleting group: " + e.getMessage());
        }
    }

    @Override
    public String handle(GenerateInvitationCommand command) {
        var group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new IllegalArgumentException("Group does not exist"));

        group.generateInvitationToken();
        groupRepository.save(group);

        return group.getInvitationToken();
    }
}
