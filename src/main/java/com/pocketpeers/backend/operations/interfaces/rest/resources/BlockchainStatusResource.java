package com.pocketpeers.backend.operations.interfaces.rest.resources;

/**
 * Cuanto le falta a un grupo para tener toda su actividad en cadena.
 *
 * <p>Es la respuesta a una pregunta que la app hacia de la forma mas cara
 * posible. El registro en blockchain es asincrono, asi que tras crear un gasto
 * la pantalla refresca hasta que aparecen los hashes; para averiguarlo recargaba
 * la lista de gastos, la de pagos, el resumen del grupo y, por cada gasto, el
 * gasto y sus pagos. Con diez gastos eran veintitres peticiones cada tres
 * segundos, y cada gasto resolvia su hash con una consulta propia.</p>
 *
 * <p>Aqui son dos conteos. La app pregunta por esto mientras espera y solo
 * recarga los datos de verdad una vez, cuando {@code pending} llega a cero.</p>
 *
 * @param pendingExpenses gastos sin transaccion en cadena
 * @param pendingPayments pagos sin transaccion en cadena
 * @param pending         la suma, para que el cliente no tenga que sumarla
 */
public record BlockchainStatusResource(
        long pendingExpenses,
        long pendingPayments,
        long pending
) {
    public static BlockchainStatusResource of(long expenses, long payments) {
        return new BlockchainStatusResource(expenses, payments, expenses + payments);
    }
}
