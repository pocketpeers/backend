package com.pocketpeers.backend.operations.infrastructure.notifications;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.UserDeviceTokenRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FcmNotificationService {
    private final UserDeviceTokenRepository tokenRepository;

    public FcmNotificationService(UserDeviceTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public void sendPaymentReminder(PaymentReminder reminder) {
        if (FirebaseApp.getApps().isEmpty()) {
            return;
        }

        Payment payment = reminder.getPayment();
        var tokens = tokenRepository.findByUser_Id(payment.getUser().getId());
        for (var deviceToken : tokens) {
            var message = Message.builder()
                    .setToken(deviceToken.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(reminder.getTitle())
                            .setBody(reminder.getBody())
                            .build())
                    .putAllData(Map.of(
                            "type", "payment_reminder",
                            "notificationId", reminder.getId().toString(),
                            "paymentId", payment.getId().toString(),
                            "expenseId", payment.getExpense().getId().toString(),
                            "groupId", payment.getExpense().getGroup().getId().toString()
                    ))
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
            } catch (FirebaseMessagingException exc) {
                if (isInvalidToken(exc)) {
                    tokenRepository.delete(deviceToken);
                }
            }
        }
    }

    private boolean isInvalidToken(FirebaseMessagingException exc) {
        var messagingErrorCode = exc.getMessagingErrorCode();
        if (messagingErrorCode == null) {
            return false;
        }
        return messagingErrorCode.name().equals("UNREGISTERED")
                || messagingErrorCode.name().equals("INVALID_ARGUMENT");
    }
}
