package com.pocketpeers.backend.operations.application.internal.eventservices;

import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.services.ExpensesNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentNotificationEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentNotificationEventHandler.class);

    private final ExpensesNotificationService notificationService;

    public PaymentNotificationEventHandler(ExpensesNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        try {
            notificationService.notifyExpenseAssigned(event.payment());
        } catch (Exception exception) {
            LOGGER.warn(
                    "Could not notify assigned expense. paymentId={}, message={}",
                    event.payment().getId(),
                    exception.getMessage()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePaymentUpdated(PaymentUpdatedEvent event) {
        var payment = event.payment();
        if (payment.getConfirmed()) {
            return;
        }
        if (PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            return;
        }
        try {
            notificationService.notifyPaymentRegistered(payment);
        } catch (Exception exception) {
            LOGGER.warn(
                    "Could not notify registered payment. paymentId={}, message={}",
                    payment.getId(),
                    exception.getMessage()
            );
        }
    }
}
