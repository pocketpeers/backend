package com.pocketpeers.backend.groups.interfaces.rest.resources;

public record ManualOverdueReminderResource(
        Long userId,
        int pushRemindersCreated,
        boolean emailSent,
        String message
) {
}
