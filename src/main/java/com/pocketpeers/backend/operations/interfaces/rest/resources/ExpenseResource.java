package com.pocketpeers.backend.operations.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResource(Long id,
                              String name,
                              BigDecimal amount,
                              Long userId,
                              Long groupId,
                              @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
                              LocalDate dueDate,
                              BigDecimal remainingAmount,
                              BigDecimal paidAmount,
                              String status,
                              Integer active,
                              String blockchainHash,
                              /*
                               * Cuando el gasto quedo escrito en la cadena.
                               * Acompaña a blockchainHash porque los dos salen
                               * del mismo eslabon: un hash sin su instante no
                               * dice a que momento corresponde la prueba.
                               */
                              java.util.Date anchoredAt,
                              java.util.Date createdAt,
                              java.util.Date updatedAt) {
}
