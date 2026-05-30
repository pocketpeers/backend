package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateExpenseResource(String name, BigDecimal amount, LocalDate dueDate) {
}
