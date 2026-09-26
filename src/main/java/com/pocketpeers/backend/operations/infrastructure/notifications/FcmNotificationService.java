package com.pocketpeers.backend.operations.infrastructure.notifications;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.domain.model.valueobjects.NotificationDeliveryStatus;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentReminderRepository;
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
    private final PaymentReminderRepository reminderRepository;

    public FcmNotificationService(UserDeviceTokenRepository tokenRepository,
                                  PaymentReminderRepository reminderRepository) {
        this.tokenRepository = tokenRepository;
        this.reminderRepository = reminderRepository;
    }

    /**
     * Envia el recordatorio y deja registrado como termino el intento.
     *
     * <p>El resultado se persiste en el propio recordatorio: antes esta
     * informacion solo existia en los logs, asi que era imposible saber despues
     * si un aviso llego o no.</p>
     */
    public void sendPaymentReminder(PaymentReminder reminder) {
        if (FirebaseApp.getApps().isEmpty()) {
            recordOutcome(reminder, NotificationDeliveryStatus.SKIPPED, "Firebase not initialized");
            return;
        }
        if (reminder.getId() == null) {
            LOGGER.warn(
                    "Skipping FCM notification because payment reminder has no id. paymentId={}, type={}",
                    reminder.getPayment().getId(),
                    reminder.getType()
            );
            return;
        }

        Payment payment = reminder.getPayment();
        var tokens = tokenRepository.findByUser_Id(payment.getUser().getId());
        if (tokens.isEmpty()) {
            // Caso distinto de un fallo de envio: a esta persona no habia por
            // donde avisarle. Confundirlo con un error de Firebase distorsiona
            // cualquier medicion del efecto de los recordatorios.
            recordOutcome(reminder, NotificationDeliveryStatus.NO_DEVICE, null);
            return;
        }

        var delivered = 0;
        String lastError = null;
        for (var deviceToken : tokens) {
            var message = Message.builder()
                    .setToken(deviceToken.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(reminder.getTitle())
                            .setBody(reminder.getBody())
                            .build())
                    .putAllData(Map.of(
                            "type", notificationType(reminder),
                            "notificationId", reminder.getId().toString(),
                            "paymentId", payment.getId().toString(),
                            "expenseId", payment.getExpense().getId().toString(),
                            "groupId", payment.getExpense().getGroup().getId().toString()
                    ))
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
                delivered++;
            } catch (FirebaseMessagingException exc) {
                lastError = String.valueOf(exc.getMessagingErrorCode());
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

        if (delivered > 0) {
            reminder.markDelivered(delivered);
            reminderRepository.save(reminder);
            return;
        }
        recordOutcome(reminder, NotificationDeliveryStatus.FAILED, lastError);
    }

    /**
     * Guarda el desenlace de la entrega.
     *
     * <p>Se aisla en un metodo para que ninguna de las rutas de salida temprana
     * se olvide de registrar por que no se envio: un recordatorio que quedara en
     * PENDING para siempre seria indistinguible de uno que nadie intento mandar.</p>
     */
    private void recordOutcome(PaymentReminder reminder, NotificationDeliveryStatus status, String detail) {
        reminder.markNotDelivered(status, detail);
        reminderRepository.save(reminder);
    }

    /**
     * Push suelto a todos los dispositivos de una persona, sin registro propio.
     *
     * <p>Para avisos que no son recordatorios de pago, como las invitaciones a
     * un grupo: ahi lo que queda guardado es la invitacion misma, y la app la
     * muestra aunque el push no llegue. Por eso es de mejor esfuerzo y nunca
     * lanza: que Firebase falle no puede deshacer la invitacion.</p>
     *
     * @return cuantos dispositivos aceptaron el mensaje.
     */
    public int sendToUser(Long userId, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            return 0;
        }
        var delivered = 0;
        for (var deviceToken : tokenRepository.findByUser_Id(userId)) {
            var message = Message.builder()
                    .setToken(deviceToken.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
                delivered++;
            } catch (FirebaseMessagingException exc) {
                LOGGER.warn(
                        "Failed to send push through FCM. userId={}, tokenId={}, type={}, messagingErrorCode={}",
                        userId,
                        deviceToken.getId(),
                        data.get("type"),
                        exc.getMessagingErrorCode()
                );
                if (isInvalidToken(exc)) {
                    tokenRepository.delete(deviceToken);
                }
            } catch (RuntimeException exc) {
                LOGGER.warn("Unexpected error sending push. userId={}, type={}", userId, data.get("type"), exc);
            }
        }
        return delivered;
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

    private String notificationType(PaymentReminder reminder) {
        return switch (reminder.getType()) {
            case DUE_IN_48_HOURS, DUE_TODAY, OVERDUE_MANUAL -> "payment_reminder";
            case EXPENSE_ASSIGNED -> "expense_assigned";
            case PAYMENT_REGISTERED -> "payment_registered";
        };
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
