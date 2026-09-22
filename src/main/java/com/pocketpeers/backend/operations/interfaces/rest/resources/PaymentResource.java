package com.pocketpeers.backend.operations.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * Un pago tal como lo ve la aplicacion movil.
 *
 * <p>Las marcas de tiempo no son la misma y conviene no confundirlas al
 * pintarlas:</p>
 *
 * <ul>
 *   <li>{@code createdAt} — cuando se registro el pago en la aplicacion.</li>
 *   <li>{@code paidAt} — cuando el usuario declara haber pagado. Nulo mientras
 *       no haya abonado nada.</li>
 *   <li>{@code anchoredAt} — cuando la operacion quedo escrita en la cadena.
 *       Es la que acompaña al hash: enseñar el hash sin ella deja al usuario
 *       sin saber a que momento corresponde la prueba que esta mirando, y la
 *       distancia entre esta marca y {@code createdAt} es justamente la
 *       latencia de anclaje.</li>
 * </ul>
 */
public record PaymentResource(Long id,
                              String description,
                              BigDecimal amount,
                              BigDecimal amountPaid,
                              String status,
                              Boolean confirmed,
                              Long userId,
                              Long expenseId,
                              String blockchainHash,
                              Date anchoredAt,
                              Date createdAt,
                              Date updatedAt,
                              LocalDateTime paidAt,
                              List<String> evidencePhotos
) {
}
