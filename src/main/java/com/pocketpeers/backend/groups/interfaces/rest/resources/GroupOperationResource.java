package com.pocketpeers.backend.groups.interfaces.rest.resources;

public record GroupOperationResource(Long id,
                                     Long groupsId,
                                     Long expenseId,
                                     Long paymentsId) {
}
