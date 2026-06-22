package com.pocketpeers.backend.operations.interfaces.rest.resources;

public record RegisterDeviceTokenResource(
        String token,
        String platform
) {
}
