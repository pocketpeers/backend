package com.pocketpeers.backend.operations.domain.model.queries;

/**
 * Todos los pagos de un grupo, en una sola consulta.
 *
 * <p>Existe para que el resumen de grupo deje de armarse pidiendo los pagos
 * gasto por gasto. Esa forma costaba una peticion por gasto, y ademas
 * secuenciales: un grupo con veinte gastos hacia veinte viajes al servidor
 * antes de poder dibujar el grafico.</p>
 */
public record GetAllPaymentsByGroupIdQuery(Long groupId) {
}
