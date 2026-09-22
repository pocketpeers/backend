package com.pocketpeers.backend.operations.domain.model.queries;

/** Gastos en los que el usuario participa: los que creo y los que debe. */
public record GetExpensesWhereUserParticipatesQuery(Long userId) {
}
