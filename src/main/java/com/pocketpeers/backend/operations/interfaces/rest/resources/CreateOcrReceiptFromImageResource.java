package com.pocketpeers.backend.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

public record CreateOcrReceiptFromImageResource(
        @NotNull
        String imageId
) {
}
