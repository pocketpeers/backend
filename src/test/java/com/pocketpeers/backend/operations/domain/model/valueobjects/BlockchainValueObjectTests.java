package com.pocketpeers.backend.operations.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlockchainValueObjectTests {

    @Test
    void contractAddressRejectsBlankValues() {
        assertThat(new ContractAddress("address-123").address()).isEqualTo("address-123");

        assertThatThrownBy(() -> new ContractAddress(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Blockchain address cannot be null or blank");
    }

    @Test
    void transactionHashAcceptsValidSolanaSignature() {
        String signature = "1".repeat(87);

        assertThat(new TransactionHash(signature).hash()).isEqualTo(signature);
    }

    @Test
    void transactionHashRejectsInvalidValues() {
        assertThatThrownBy(() -> new TransactionHash(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction hash cannot be null or blank");

        assertThatThrownBy(() -> new TransactionHash("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Solana transaction signature format (Must be 87-88 Base58 characters)");

        assertThatThrownBy(() -> new TransactionHash("00000000000000000000000000000000000000000000000000000000000000000000000000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Solana transaction signature format (Must be 87-88 Base58 characters)");
    }
}
