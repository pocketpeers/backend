package com.pocketpeers.backend.groups.domain.services;

import com.pocketpeers.backend.groups.domain.model.aggregates.GroupOperation;
import com.pocketpeers.backend.groups.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface GroupOperationQueryService {
    List<GroupOperation> handle(GetAllGroupOperationsQuery query);
    Optional<GroupOperation> handle(GetGroupOperationByIdQuery query);
    List<GroupOperation> handle(GetAllGroupOperationsByGroupIdQuery query);
    Optional<GroupOperation> handle(GetGroupOperationByGroupIdAndExpenseIdAndPaymentId query);
    List<GroupOperation> handle(GetAllGroupOperationByExpenseIdQuery query);
}
