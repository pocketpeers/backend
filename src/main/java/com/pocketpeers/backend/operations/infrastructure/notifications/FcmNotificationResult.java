package com.pocketpeers.backend.operations.infrastructure.notifications;

public record FcmNotificationResult(
        boolean firebaseInitialized,
        int registeredDeviceTokens,
        int sent,
        int failed,
        int removedInvalidTokens,
        String lastErrorCode,
        String lastErrorMessage
) {
}
