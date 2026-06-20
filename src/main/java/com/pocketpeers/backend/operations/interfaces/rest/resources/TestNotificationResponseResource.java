package com.pocketpeers.backend.operations.interfaces.rest.resources;

public record TestNotificationResponseResource(
        boolean firebaseInitialized,
        int registeredDeviceTokens,
        int sent,
        int failed,
        int removedInvalidTokens,
        String lastErrorCode,
        String lastErrorMessage,
        String message
) {
}
