package com.pocketpeers.backend.operations.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record ContractAddress(
    String address
) {
    public ContractAddress {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Blockchain address cannot be null or blank");
        }
        /*if (!address.startsWith("0x") || address.length() != 42) {
            throw new IllegalArgumentException("Invalid blockchain address format");
        }*/
    }
}
