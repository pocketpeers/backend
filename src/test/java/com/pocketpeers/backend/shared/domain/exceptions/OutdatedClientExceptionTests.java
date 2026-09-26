package com.pocketpeers.backend.shared.domain.exceptions;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutdatedClientExceptionTests {

    @Test
    void missingHeaderMeansAnOldApp() {
        assertThatThrownBy(() -> OutdatedClientException.requireSupported(null))
                .isInstanceOf(OutdatedClientException.class)
                .hasMessageContaining("Actualiza la app");
    }

    @Test
    void olderOrUnreadableVersionsAreRejected() {
        assertThatThrownBy(() -> OutdatedClientException.requireSupported("1"))
                .isInstanceOf(OutdatedClientException.class);
        assertThatThrownBy(() -> OutdatedClientException.requireSupported("abc"))
                .isInstanceOf(OutdatedClientException.class);
    }

    @Test
    void currentVersionIsAccepted() {
        assertThatCode(() -> OutdatedClientException.requireSupported(
                String.valueOf(OutdatedClientException.MIN_SUPPORTED_APP_VERSION)))
                .doesNotThrowAnyException();
    }
}
