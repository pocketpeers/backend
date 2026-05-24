package com.pocketpeers.backend.groups.domain.services;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.queries.GetALLGroupByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllMembersInGroupQuery;

import java.util.List;

public interface GroupMemberQueryService {
    List<GroupMember> handle(GetAllMembersInGroupQuery query);
    List<GroupMember> handle(GetALLGroupByUserIdQuery query);
}
