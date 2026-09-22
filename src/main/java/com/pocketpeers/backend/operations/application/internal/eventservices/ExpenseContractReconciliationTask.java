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
     * Tres minutos, contados desde que termina la pasada anterior.
     *
     * <p>Eran quince, y el motivo era real: el manejador es asincrono, asi que
     * publicar el evento retorna de inmediato mientras el despliegue sigue en
     * curso —la espera de confirmacion admite hasta 90 s y la sincronizacion de
     * pagos reintenta hasta 60 s mas—. Con un intervalo corto, la pasada
     * siguiente encontraba el mismo gasto todavia sin contrato y lanzaba un
     * segundo despliegue en paralelo, con su propia comision.</p>
     *
     * <p>Lo que estaba mal era la forma de evitarlo. Espaciar las pasadas
     * protege del despliegue duplicado a costa de que un gasto realmente caido
     * espere hasta quince minutos, y eso fue exactamente lo que le paso al
     * gasto 1: su transaccion se cayo a las 18:44 y nadie volvio a intentarlo
     * hasta la pasada de las 19:02. Ahora quien protege de la carrera es el
     * corte por antiguedad de {@link #RECONCILIATION_MIN_AGE_MILLIS}, que deja
     * fuera los gastos cuyo despliegue puede seguir en vuelo. Separadas las dos
     * responsabilidades, el intervalo puede bajar a lo que se quiera tardar en
     * recoger un fallo.</p>
     */
    private static final long RECONCILIATION_INTERVAL_MILLIS = 3 * 60 * 1000L;

    /**
     * Antiguedad minima para que un gasto entre en la reconciliacion.
     *
     * <p>Cinco minutos: por encima de los 90 s de espera de confirmacion mas
     * los 60 s de reintentos de pagos, con margen. Por debajo de esa edad un
     * gasto sin contrato no es un gasto caido, es uno que todavia se esta
     * desplegando, y reintentarlo pagaria una segunda comision por el mismo
     * trabajo.</p>
     */
    private static final long RECONCILIATION_MIN_AGE_MILLIS = 5 * 60 * 1000L;

    /** Un minuto, para no competir con el arranque de la aplicacion. */
    private static final long RECONCILIATION_INITIAL_DELAY_MILLIS = 60 * 1000L;

    /** Separacion entre un gasto y el siguiente. Ver {@link #waitBetweenExpenses()}. */
    private static final long RECONCILIATION_SPACING_MILLIS = 1_000L;

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
        var cutoff = new java.util.Date(System.currentTimeMillis() - RECONCILIATION_MIN_AGE_MILLIS);
        var orphans = expenseRepository.findActiveWithoutContract(cutoff);
        if (orphans.isEmpty()) {
            // Sin traza cuando no hay nada que hacer: este metodo corre cada
            // pocos minutos y un log por pasada solo ensuciaria la consola.
            return;
        }

        LOGGER.info("Gastos sin contrato detectados, reintentando el registro. cantidad={}", orphans.size());
        for (var expense : orphans) {
            LOGGER.info("Reintentando contrato de gasto. expenseId={}, name={}",
                    expense.getId(), expense.getName());
            applicationEventPublisher.publishEvent(new ExpenseCreatedEvent(expense));
            waitBetweenExpenses();
        }
    }

    /**
     * Separa los reintentos en el tiempo.
     *
     * <p>El manejador es asincrono, asi que publicar la lista entera de golpe
     * arranca un hilo por gasto y todos atacan el RPC a la vez. Cada gasto son
     * ya varias peticiones —blockhash, envio, sondeo de confirmacion, lectura
     * de la cuenta— y multiplicadas por los pagos de cada uno. Con ocho gastos
     * eso basta para que el nodo publico de devnet empiece a devolver 429, y
     * entonces no falla uno: fallan casi todos a la vez.</p>
     *
     * <p>Un segundo entre publicaciones no serializa nada —los hilos siguen
     * solapandose— pero escalona el arranque lo suficiente para que la rafaga
     * no llegue toda en el mismo instante. Reconciliar es una tarea de fondo
     * que corre cada quince minutos: que tarde unos segundos mas no le importa
     * a nadie.</p>
     */
    private void waitBetweenExpenses() {
        try {
            Thread.sleep(RECONCILIATION_SPACING_MILLIS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
