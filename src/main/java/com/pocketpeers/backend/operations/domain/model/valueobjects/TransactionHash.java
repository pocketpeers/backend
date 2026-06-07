package com.pocketpeers.backend.operations.domain.model.valueobjects;

public record TransactionHash(
        String hash
) {
    public TransactionHash {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash cannot be null or blank");
        }

        if (hash.length() < 87 || hash.length() > 88 || !hash.matches("^[1-9A-HJ-NP-Za-km-z]+$")) {
            throw new IllegalArgumentException("Invalid Solana transaction signature format (Must be 87-88 Base58 characters)");
        }
    }
}