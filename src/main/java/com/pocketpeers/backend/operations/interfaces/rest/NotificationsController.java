package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.operations.domain.services.ExpensesNotificationService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentReminderRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.UserDeviceTokenRepository;
import com.pocketpeers.backend.operations.domain.model.entities.UserDeviceToken;
import com.pocketpeers.backend.operations.infrastructure.notifications.FcmNotificationService;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentReminderResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.RegisterDeviceTokenResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.DeviceTokenStatusResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.TestNotificationResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.TestNotificationResponseResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.UnreadCountResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.PaymentReminderResourceFromEntityAssembler;
import com.pocketpeers.backend.shared.interfaces.rest.resources.MessageResource;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Payment reminder notifications")
public class NotificationsController {
    /** Tope de pagina, para que un cliente no pida el historial entero de golpe. */
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentReminderRepository reminderRepository;
    private final UserDeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final ExpensesNotificationService notificationService;
    private final FcmNotificationService fcmNotificationService;

    public NotificationsController(
            PaymentReminderRepository reminderRepository,
            UserDeviceTokenRepository deviceTokenRepository,
            UserRepository userRepository,
            ExpensesNotificationService notificationService,
            FcmNotificationService fcmNotificationService
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.fcmNotificationService = fcmNotificationService;
    }

    @GetMapping("/unread")
    public ResponseEntity<List<PaymentReminderResource>> getUnread(Authentication authentication) {
        var user = authenticatedUser(authentication);
        var reminders = reminderRepository.findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(PaymentReminderResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(reminders);
    }

    /**
     * Historial completo del usuario, leidas y no leidas.
     *
     * <p>Paginado porque la lista solo crece. El limite se acota en el servidor
     * para que un cliente no pueda pedir el historial entero de una sola vez.</p>
     */
    @GetMapping
    public ResponseEntity<List<PaymentReminderResource>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication
    ) {
        var user = authenticatedUser(authentication);
        var safePage = Math.max(0, page);
        var safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        var reminders = reminderRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId(), PageRequest.of(safePage, safeSize))
                .stream()
                .map(PaymentReminderResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(reminders);
    }

    /** Cuantas quedan sin leer, para el indicador de la interfaz. */
    @GetMapping("/unread/count")
    public ResponseEntity<UnreadCountResource> getUnreadCount(Authentication authentication) {
        var user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                new UnreadCountResource(reminderRepository.countByUser_IdAndReadAtIsNull(user.getId())));
    }

    /** Marca como leidas todas las pendientes del usuario. */
    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<MessageResource> markAllRead(Authentication authentication) {
        var user = authenticatedUser(authentication);
        var updated = reminderRepository.markAllReadForUser(user.getId(), LocalDateTime.now());
        return ResponseEntity.ok(new MessageResource("Marked " + updated + " notifications as read"));
    }

    /** Borra una notificacion del historial del usuario. */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<MessageResource> delete(@PathVariable Long notificationId, Authentication authentication) {
        var user = authenticatedUser(authentication);
        var reminder = reminderRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        // La comprobacion de propiedad no es opcional: sin ella, cualquiera con
        // sesion podria borrar las notificaciones de otra persona probando ids.
        if (!reminder.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Notification does not belong to authenticated user");
        }
        reminderRepository.delete(reminder);
        return ResponseEntity.ok(new MessageResource("Notification deleted"));
    }

    /**
     * Vacia el historial leido del usuario.
     *
     * <p>Solo borra las que ya vio. Arrasar tambien con las pendientes haria que
     * alguien pierda un aviso de vencimiento sin haberlo leido nunca.</p>
     */
    @DeleteMapping("/read")
    @Transactional
    public ResponseEntity<MessageResource> deleteRead(Authentication authentication) {
        var user = authenticatedUser(authentication);
        var deleted = reminderRepository.deleteReadForUser(user.getId());
        return ResponseEntity.ok(new MessageResource("Deleted " + deleted + " notifications"));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<MessageResource> markRead(@PathVariable Long notificationId, Authentication authentication) {
        var user = authenticatedUser(authentication);
        var reminder = reminderRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!reminder.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Notification does not belong to authenticated user");
        }
        reminder.markRead();
        reminderRepository.save(reminder);
        return ResponseEntity.ok(new MessageResource("Notification marked as read"));
    }

    @PostMapping("/device-tokens")
    public ResponseEntity<DeviceTokenStatusResource> registerDeviceToken(
            @RequestBody RegisterDeviceTokenResource resource,
            Authentication authentication
    ) {
        var user = authenticatedUser(authentication);
        if (resource.token() == null || resource.token().isBlank()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var token = deviceTokenRepository.findByToken(resource.token())
                .map(existingToken -> {
                    existingToken.updateOwner(user, resource.platform());
                    return existingToken;
                })
                .orElseGet(() -> new UserDeviceToken(user, resource.token(), resource.platform()));
        deviceTokenRepository.save(token);
        var tokenCount = deviceTokenRepository.findByUser_Id(user.getId()).size();
        return ResponseEntity.ok(new DeviceTokenStatusResource(
                user.getId(),
                user.getUsername(),
                tokenCount,
                "Device token registered"
        ));
    }

    @GetMapping("/device-tokens/me")
    public ResponseEntity<DeviceTokenStatusResource> getMyDeviceTokenStatus(Authentication authentication) {
        var user = authenticatedUser(authentication);
        var tokenCount = deviceTokenRepository.findByUser_Id(user.getId()).size();
        return ResponseEntity.ok(new DeviceTokenStatusResource(
                user.getId(),
                user.getUsername(),
                tokenCount,
                "Found " + tokenCount + " registered device tokens"
        ));
    }

    @DeleteMapping("/device-tokens")
    public ResponseEntity<MessageResource> deleteDeviceToken(
            @RequestBody RegisterDeviceTokenResource resource,
            Authentication authentication
    ) {
        authenticatedUser(authentication);
        if (resource.token() != null && !resource.token().isBlank()) {
            deviceTokenRepository.deleteByToken(resource.token());
        }
        return ResponseEntity.ok(new MessageResource("Device token deleted"));
    }

    @PostMapping("/run-reminders")
    public ResponseEntity<MessageResource> runReminders() {
        var created = notificationService.createPaymentReminders();
        return ResponseEntity.ok(new MessageResource("Created " + created + " reminders"));
    }

    @PostMapping("/test")
    public ResponseEntity<TestNotificationResponseResource> sendTestNotification(
            @RequestBody(required = false) TestNotificationResource resource,
            Authentication authentication
    ) {
        var user = authenticatedUser(authentication);
        var title = valueOrDefault(resource == null ? null : resource.title(), "PocketPeers test notification");
        var body = valueOrDefault(resource == null ? null : resource.body(), "This is a test notification from the backend.");
        var result = fcmNotificationService.sendTestNotification(user, title, body);
        var message = result.firebaseInitialized()
                ? "Sent " + result.sent() + " test notifications"
                : "Firebase Admin SDK is not initialized";
        return ResponseEntity.ok(new TestNotificationResponseResource(
                result.firebaseInitialized(),
                result.registeredDeviceTokens(),
                result.sent(),
                result.failed(),
                result.removedInvalidTokens(),
                result.lastErrorCode(),
                result.lastErrorMessage(),
                message
        ));
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private com.pocketpeers.backend.users.domain.model.aggregates.User authenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
