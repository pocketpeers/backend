package com.pocketpeers.backend.groups.application.internal.queryservices;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.queries.GetALLGroupByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllMembersInGroupQuery;
import com.pocketpeers.backend.groups.domain.services.GroupMemberQueryService;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupMemberQueryServiceImpl implements GroupMemberQueryService {

    private final GroupMemberRepository groupMemberRepository;

    public GroupMemberQueryServiceImpl(GroupMemberRepository groupMemberRepository) {
        this.groupMemberRepository = groupMemberRepository;
    }

    @Override
    public List<GroupMember> handle(GetAllMembersInGroupQuery query) {
        return groupMemberRepository.findAllByGroupId(query.groupId());
    }

    @Override
    public List<GroupMember> handle(GetALLGroupByUserIdQuery query) {
        return groupMemberRepository.findAllByUserInformationId(query.userId());
    }



}
