package com.pocketpeers.backend.groups.domain.model.queries;

public record GetGroupInvitationLinkQuery(
        Long groupId,
        Long adminId  
) {}