package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.operations.domain.services.ExpensesNotificationService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentReminderRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.UserDeviceTokenRepository;
import com.pocketpeers.backend.operations.domain.model.entities.UserDeviceToken;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentReminderResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.RegisterDeviceTokenResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.PaymentReminderResourceFromEntityAssembler;
import com.pocketpeers.backend.shared.interfaces.rest.resources.MessageResource;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Payment reminder notifications")
public class NotificationsController {
    private final PaymentReminderRepository reminderRepository;
    private final UserDeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final ExpensesNotificationService notificationService;

    public NotificationsController(
            PaymentReminderRepository reminderRepository,
            UserDeviceTokenRepository deviceTokenRepository,
            UserRepository userRepository,
            ExpensesNotificationService notificationService
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
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
    public ResponseEntity<MessageResource> registerDeviceToken(
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
        return ResponseEntity.ok(new MessageResource("Device token registered"));
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

    private com.pocketpeers.backend.users.domain.model.aggregates.User authenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
