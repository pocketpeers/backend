package com.pocketpeers.backend.groups.application.internal.queryservices;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupByIdQuery;
import com.pocketpeers.backend.groups.domain.services.GroupQueryService;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
@AllArgsConstructor
public class GroupQueryServiceImpl implements GroupQueryService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;


    @Override
    public Optional<Group> handle(GetGroupByIdQuery query) {
        return groupRepository.findById(query.groupId());
    }

    @Override
    public List<Group> handle(GetAllGroupsQuery query) {
        return groupRepository.findAll();
    }

    @Override
    public List<Group> handle(GetAllGroupsByUserIdQuery query) {
        return groupRepository.findAllByUserId(query.userId());
    }


}
