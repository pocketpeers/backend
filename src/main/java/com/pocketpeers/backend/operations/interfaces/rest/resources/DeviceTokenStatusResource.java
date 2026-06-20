package com.pocketpeers.backend.operations.interfaces.rest.resources;

public record DeviceTokenStatusResource(
        Long userId,
        String username,
        int registeredDeviceTokens,
        String message
) {
}
