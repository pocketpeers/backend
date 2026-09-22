package com.pocketpeers.backend.operations.domain.model.commands;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param username quien pide el cambio. Va en el comando y no se deduce del
 *                 gasto porque la autorizacion la decide el servicio, y sin
 *                 este dato no tendria con que compararlo.
 */
public record UpdateExpenseCommand(Long id, String name, BigDecimal amount, LocalDate dueDate,
                                   String username) {
}
