package com.pocketpeers.backend.groups.domain.services;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupByIdQuery;

import java.util.List;
import java.util.Optional;

public interface GroupQueryService {
    Optional<Group> handle(GetGroupByIdQuery query);
    List<Group> handle(GetAllGroupsQuery query);
    List<Group> handle(GetAllGroupsByUserIdQuery query);
}
