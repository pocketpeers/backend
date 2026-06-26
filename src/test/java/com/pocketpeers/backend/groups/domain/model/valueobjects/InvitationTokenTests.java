package com.pocketpeers.backend.groups.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InvitationTokenTests {

    @Test
    void rejectsBlankTokens() {
        assertThatThrownBy(() -> new InvitationToken(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedTokenIsNotBlank() {
        InvitationToken token = new InvitationToken();

        assertThat(token.getToken()).isNotBlank();
    }

    @Test
    void keepsProvidedToken() {
        InvitationToken token = new InvitationToken("abc-123");

        assertThat(token.getToken()).isEqualTo("abc-123");
    }
}
