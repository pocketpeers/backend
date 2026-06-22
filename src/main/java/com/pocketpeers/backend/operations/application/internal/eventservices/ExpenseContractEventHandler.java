package com.pocketpeers.backend.operations.application.internal.eventservices;

import com.pocketpeers.backend.operations.domain.model.events.ExpenseCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.operations.domain.ports.out.ExpenseSmartContractPort;
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
    private static final int PAYMENT_CONTRACT_SYNC_ATTEMPTS = 5;
    private static final long PAYMENT_CONTRACT_SYNC_DELAY_MILLIS = 2_000;

    private ExpenseSmartContractPort expenseSmartContractPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handler(ExpenseCreatedEvent event) {
        try {
            LOGGER.info("Handling ExpenseCreatedEvent for expense: {}", event.expense().getName());

            ContractAddress contractAddress = expenseSmartContractPort.deployExpenseContract(event.expense());

            LOGGER.info("Expense contract deployed with address: {}", contractAddress.address());
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
        for (var attempt = 1; attempt <= PAYMENT_CONTRACT_SYNC_ATTEMPTS; attempt++) {
            try {
                TransactionHash transactionHash = expenseSmartContractPort.addPaymentToExpenseContract(
                            event.payment().getExpense(),
                            event.payment()
                    );
                LOGGER.info("Payment added to expense contract with transaction hash: {}", transactionHash.hash());
                return;
            } catch (Exception exception) {
                if (attempt == PAYMENT_CONTRACT_SYNC_ATTEMPTS) {
                    LOGGER.warn(
                            "Could not add payment to expense contract. paymentId={}, attempts={}, message={}",
                            event.payment().getId(),
                            attempt,
                            exception.getMessage()
                    );
                    return;
                }
                waitBeforeRetry(event.payment().getId(), attempt, exception);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handler(PaymentUpdatedEvent event) {
        try {
            TransactionHash transactionHash = expenseSmartContractPort.updatePaymentStatus(
                    event.payment(),
                    PaymentStatus.valueOf(event.payment().getStatus())
            );
            LOGGER.info("Payment status updated in expense contract with transaction hash: {}", transactionHash.hash());
        } catch (Exception exception) {
            LOGGER.warn(
                    "Could not update payment status in expense contract. paymentId={}, message={}",
                    event.payment().getId(),
                    exception.getMessage()
            );
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
