package com.pocketpeers.backend.users.infrastructure.tokens.jwt.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * El arranque no debe tolerar un secreto que no protege nada.
 *
 * <p>Con el valor de plantilla la aplicacion levantaba en silencio y firmaba
 * tokens validos con una cadena publicada en el repositorio.</p>
 */
class TokenServiceSecretGuardTests {

    private TokenServiceImpl serviceWith(String secret) {
        var service = new TokenServiceImpl();
        ReflectionTestUtils.setField(service, "secret", secret);
        return service;
    }

    @Test
    @DisplayName("El secreto de plantilla impide arrancar")
    void rechazaPlantilla() {
        assertThatThrownBy(() -> serviceWith("WriteHereYourSecretStringForTokenSigningCredentials")
                .rejectInsecureSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTHORIZATION_JWT_SECRET");
    }

    @Test
    @DisplayName("Un secreto vacio impide arrancar")
    void rechazaVacio() {
        assertThatThrownBy(() -> serviceWith("   ").rejectInsecureSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Menos de 256 bits impide arrancar, porque HS256 los exige")
    void rechazaCorto() {
        assertThatThrownBy(() -> serviceWith("corto").rejectInsecureSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demasiado corto");
    }

    @Test
    @DisplayName("Un secreto generado de verdad arranca sin quejarse")
    void aceptaUnoValido() {
        var real = "9Gk2mQvTzR7xW4pLbN8sJdH3yC6uA1eF0iO5rXtVgZkMnQwErTyUiOpAsDfGhJkL";
        assertThat(real.length()).isGreaterThanOrEqualTo(32);
        assertThatCode(() -> serviceWith(real).rejectInsecureSecret()).doesNotThrowAnyException();
    }
}
