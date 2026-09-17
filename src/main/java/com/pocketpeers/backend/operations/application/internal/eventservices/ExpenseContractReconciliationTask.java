package com.pocketpeers.backend.operations.application.internal.eventservices;

import com.pocketpeers.backend.operations.domain.model.events.ExpenseCreatedEvent;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recupera los gastos que se quedaron sin contrato en la cadena.
 *
 * <p>El despliegue corre fuera de la transaccion del gasto —para que un fallo
 * de blockchain no impida registrarlo— y en un unico intento: si falla, el
 * manejador deja un aviso en el log y nadie vuelve a intentarlo. Ese gasto
 * queda sin trazabilidad y, ademas, ninguno de sus pagos puede sincronizarse,
 * porque la sincronizacion de pagos exige que el contrato del gasto exista.</p>
 *
 * <p>Sin algo asi, cada caida pasajera de la red cuesta un gasto de forma
 * permanente. En un estudio de campo de varias semanas eso ocurre, y el dato
 * perdido no se recupera despues.</p>
 */
@Component
@AllArgsConstructor
public class ExpenseContractReconciliationTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpenseContractReconciliationTask.class);

    /**
     * Quince minutos, contados desde que termina la pasada anterior.
     *
     * <p>El margen no es estetico. El manejador es asincrono, asi que publicar
     * el evento retorna de inmediato mientras el despliegue sigue en curso: la
     * espera de confirmacion admite hasta 90 s y la sincronizacion de pagos
     * reintenta hasta 60 s mas. Con un intervalo corto, la pasada siguiente
     * encontraria el mismo gasto todavia sin contrato y lanzaria un segundo
     * despliegue en paralelo, con su propia comision.</p>
     */
    private static final long RECONCILIATION_INTERVAL_MILLIS = 15 * 60 * 1000L;

    /** Un minuto, para no competir con el arranque de la aplicacion. */
    private static final long RECONCILIATION_INITIAL_DELAY_MILLIS = 60 * 1000L;

    private final ExpenseRepository expenseRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Reintenta publicando el mismo evento que emite la creacion del gasto.
     *
     * <p>Se reusa esa ruta a proposito, en vez de llamar al despliegue de forma
     * directa: el manejador ya sabe adoptar una cuenta que exista en la red sin
     * crear otra —el caso del gasto cuya cuenta si se creo pero cuya fila se
     * perdio— y ya sincroniza los pagos pendientes despues. Duplicar esa logica
     * aqui abriria la puerta a que las dos copias se separen.</p>
     *
     * <p>La lectura va en transaccion de solo lectura, pero los eventos se
     * publican fuera de cualquier escritura: el manejador escucha AFTER_COMMIT
     * con {@code fallbackExecution}, de modo que corre igual sin transaccion.</p>
     */
    @Scheduled(
            fixedDelay = RECONCILIATION_INTERVAL_MILLIS,
            initialDelay = RECONCILIATION_INITIAL_DELAY_MILLIS
    )
    @Transactional(readOnly = true)
    public void redeployExpensesWithoutContract() {
        var orphans = expenseRepository.findActiveWithoutContract();
        if (orphans.isEmpty()) {
            // Sin traza cuando no hay nada que hacer: este metodo corre cada
            // quince minutos y un log por pasada solo ensuciaria la consola.
            return;
        }

        LOGGER.info("Gastos sin contrato detectados, reintentando el registro. cantidad={}", orphans.size());
        for (var expense : orphans) {
            LOGGER.info("Reintentando contrato de gasto. expenseId={}, name={}",
                    expense.getId(), expense.getName());
            applicationEventPublisher.publishEvent(new ExpenseCreatedEvent(expense));
        }
    }
}
