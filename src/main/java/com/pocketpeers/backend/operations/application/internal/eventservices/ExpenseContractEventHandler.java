package com.pocketpeers.backend.operations.application.internal.eventservices;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.events.ExpenseCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.operations.domain.exceptions.PermanentContractSyncException;
import com.pocketpeers.backend.operations.domain.ports.out.ExpenseSmartContractPort;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseChainRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@AllArgsConstructor
public class ExpenseContractEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpenseContractEventHandler.class);
    // Payments can be created almost at the same time as the expense. These
    // retries give the expense contract enough time to be deployed before the
    // payment is recorded on-chain.
    private static final int PAYMENT_CONTRACT_SYNC_ATTEMPTS = 30;
    private static final long PAYMENT_CONTRACT_SYNC_DELAY_MILLIS = 2_000;

    private ExpenseSmartContractPort expenseSmartContractPort;
    private PaymentRepository paymentRepository;
    private ExpenseChainRepository expenseChainRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handler(ExpenseCreatedEvent event) {
        try {
            // Contract deployment is intentionally outside the original expense
            // transaction. A blockchain failure should not roll back the expense
            // stored in the application database.
            LOGGER.info("Handling ExpenseCreatedEvent for expense: {}", event.expense().getName());

            ContractAddress contractAddress = expenseSmartContractPort.deployExpenseContract(event.expense());

            LOGGER.info("Expense contract deployed with address: {}", contractAddress.address());
            syncExistingPaymentsForExpense(event.expense().getId());
        } catch (Exception exception) {
            LOGGER.warn(
                    "Could not deploy expense contract. expenseId={}, message={}",
                    event.expense().getId(),
                    exception.getMessage()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handler(PaymentCreatedEvent event) {
        // Si la cadena del gasto todavia no existe, no hay donde anclar: la ruta
        // de ExpenseCreatedEvent sincronizara todos sus pagos en cuanto termine
        // el despliegue.
        //
        // La pregunta se le hace a `expense_chains`, que es donde vive el estado
        // del programa actual. Antes se le hacia a `expense_contracts`, del
        // programa anterior, que dejo de escribirse: desde entonces esta guarda
        // se cumplia siempre y este camino nunca sincronizaba nada. No se noto
        // porque los pagos quedaban anclados igual por las otras dos rutas, que
        // es justamente lo que hace peligroso un vestigio asi.
        if (expenseChainRepository.findByExpense(event.payment().getExpense()).isEmpty()) {
            LOGGER.info(
                    "Payment contract sync skipped until expense chain exists. paymentId={}, expenseId={}",
                    event.payment().getId(),
                    event.payment().getExpense().getId()
            );
            return;
        }
        syncPayment(event.payment());
    }

    /**
     * Un pago actualizado toma exactamente la misma ruta que uno nuevo.
     *
     * <p>Antes eran dos operaciones distintas porque cada pago tenia su cuenta
     * en la cadena: una la creaba y otra la sobrescribia. Sin esa cuenta, tanto
     * registrar como corregir son lo mismo —un eslabon mas en la cadena del
     * gasto— y mantener dos caminos separados solo serviria para que se
     * separaran tambien en el comportamiento.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handler(PaymentUpdatedEvent event) {
        syncPayment(event.payment());
    }

    private void syncExistingPaymentsForExpense(Long expenseId) {
        // Handles payments that were persisted before Solana finished creating
        // the expense PDA.
        var payments = paymentRepository.findAllByExpenseId(expenseId);
        for (var payment : payments) {
            syncPayment(payment);
        }
    }

    private void syncPayment(Payment payment) {
        for (var attempt = 1; attempt <= PAYMENT_CONTRACT_SYNC_ATTEMPTS; attempt++) {
            try {
                TransactionHash transactionHash = expenseSmartContractPort.recordPayment(
                        payment.getExpense(),
                        payment
                );
                LOGGER.info("Payment recorded in expense contract with transaction hash: {}", transactionHash.hash());
                return;
            } catch (PermanentContractSyncException exception) {
                // Los reintentos existen para esperar a que el contrato del gasto
                // termine de desplegarse, que es una condicion pasajera. Una falla
                // permanente no mejora esperando y cada intento firma y envia una
                // transaccion real, asi que reintentarla solo multiplica el costo.
                LOGGER.warn(
                        "Payment contract sync abandoned, retrying would not help. paymentId={}, message={}",
                        payment.getId(),
                        exception.getMessage()
                );
                return;
            } catch (Exception exception) {
                if (attempt == PAYMENT_CONTRACT_SYNC_ATTEMPTS) {
                    LOGGER.warn(
                            "Could not record payment in expense contract. paymentId={}, attempts={}, message={}",
                            payment.getId(),
                            attempt,
                            exception.getMessage()
                    );
                    return;
                }
                waitBeforeRetry(payment.getId(), attempt, exception);
            }
        }
    }

    private void waitBeforeRetry(Long paymentId, int attempt, Exception exception) {
        LOGGER.info(
                "Payment contract sync will retry. paymentId={}, attempt={}, message={}",
                paymentId,
                attempt,
                exception.getMessage()
        );
        try {
            Thread.sleep(PAYMENT_CONTRACT_SYNC_DELAY_MILLIS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

}
