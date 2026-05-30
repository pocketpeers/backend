package com.pocketpeers.backend.operations.domain.model.queries;

import java.time.LocalDate;

public record GetAllExpensesByDueDate(LocalDate dueDate) {
}
