package com.pocketpeers.backend.operations.infrastructure.notifications;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.UserDeviceTokenRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FcmNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FcmNotificationService.class);

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
                LOGGER.warn(
                        "Failed to send payment reminder through FCM. tokenId={}, errorCode={}, messagingErrorCode={}, message={}",
                        deviceToken.getId(),
                        exc.getErrorCode(),
                        exc.getMessagingErrorCode(),
                        exc.getMessage()
                );
                if (isInvalidToken(exc)) {
                    tokenRepository.delete(deviceToken);
                }
            }
        }
    }

    public FcmNotificationResult sendTestNotification(User user, String title, String body) {
        var tokens = tokenRepository.findByUser_Id(user.getId());
        if (FirebaseApp.getApps().isEmpty()) {
            return new FcmNotificationResult(false, tokens.size(), 0, 0, 0, null, null);
        }

        var sent = 0;
        var failed = 0;
        var removedInvalidTokens = 0;
        String lastErrorCode = null;
        String lastErrorMessage = null;
        for (var deviceToken : tokens) {
            var message = Message.builder()
                    .setToken(deviceToken.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("high_importance_channel")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build())
                            .build())
                    .putAllData(Map.of(
                            "type", "test_notification"
                    ))
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
                sent++;
            } catch (FirebaseMessagingException exc) {
                failed++;
                lastErrorCode = fcmErrorCode(exc);
                lastErrorMessage = exc.getMessage();
                LOGGER.warn(
                        "Failed to send test notification through FCM. userId={}, tokenId={}, errorCode={}, messagingErrorCode={}, message={}",
                        user.getId(),
                        deviceToken.getId(),
                        exc.getErrorCode(),
                        exc.getMessagingErrorCode(),
                        exc.getMessage()
                );
                if (isInvalidToken(exc)) {
                    tokenRepository.delete(deviceToken);
                    removedInvalidTokens++;
                }
            }
        }
        return new FcmNotificationResult(
                true,
                tokens.size(),
                sent,
                failed,
                removedInvalidTokens,
                lastErrorCode,
                lastErrorMessage
        );
    }

    private String fcmErrorCode(FirebaseMessagingException exc) {
        var messagingErrorCode = exc.getMessagingErrorCode();
        if (messagingErrorCode != null) {
            return messagingErrorCode.name();
        }
        return exc.getErrorCode() == null ? null : exc.getErrorCode().name();
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
