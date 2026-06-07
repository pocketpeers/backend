package com.pocketpeers.backend.groups.domain.model.queries;

public record GetGroupOperationByGroupIdAndExpenseIdAndPaymentId(Long groupId, Long expenseId, Long paymentId) {
}
