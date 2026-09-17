package com.pocketpeers.backend.users.domain.model;

import com.pocketpeers.backend.users.domain.model.entities.PasswordResetCode;
import com.pocketpeers.backend.users.domain.model.valueobjects.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica las reglas de contrasena y el ciclo de vida del codigo de recuperacion.
 *
 * <p>Son las piezas de la autenticacion que se pueden probar sin base de datos ni
 * servidor de correo, y son justamente donde un descuido tiene consecuencias de
 * seguridad.</p>
 */
class PasswordSecurityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 10, 0);

    // ------------------------------------------------------------------
    // Politica de contrasenas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una contrasena valida no devuelve error")
    void contrasenaValidaEsAceptada() {
        assertNull(PasswordPolicy.validationError("pocket2026"));
        assertTrue(PasswordPolicy.isValid("mi clave 99"));
    }

    @Test
    @DisplayName("El motivo del rechazo explica que falta, no solo que fallo")
    void elMotivoDelRechazoEsUtil() {
        assertTrue(PasswordPolicy.validationError("corta1").contains("8 caracteres"));
        assertTrue(PasswordPolicy.validationError("12345678").contains("letra"));
        assertTrue(PasswordPolicy.validationError("solotexto").contains("número"));
        assertNotNull(PasswordPolicy.validationError(""));
        assertNotNull(PasswordPolicy.validationError(null));
    }

    @Test
    @DisplayName("Se rechazan contrasenas mas largas de lo que BCrypt puede usar")
    void contrasenaDemasiadoLargaSeRechaza() {
        // BCrypt ignora todo lo que pase de 72 bytes. Aceptarla daria la falsa
        // impresion de que los caracteres extra aportan seguridad.
        String larga = "a1" + "x".repeat(80);

        assertFalse(PasswordPolicy.isValid(larga));
        assertTrue(PasswordPolicy.validationError(larga).contains("72"));
    }

    // ------------------------------------------------------------------
    // Ciclo de vida del codigo de recuperacion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un codigo recien emitido sirve")
    void codigoRecienEmitidoEsUsable() {
        var code = new PasswordResetCode(1L, "hash", NOW);

        assertTrue(code.isUsable(NOW));
        assertFalse(code.isExpired(NOW));
        assertFalse(code.isUsed());
    }

    @Test
    @DisplayName("El codigo vence a los 15 minutos")
    void codigoVenceTrasLaVentana() {
        var code = new PasswordResetCode(1L, "hash", NOW);

        assertTrue(code.isUsable(NOW.plusMinutes(PasswordResetCode.EXPIRATION_MINUTES - 1)));
        assertFalse(code.isUsable(NOW.plusMinutes(PasswordResetCode.EXPIRATION_MINUTES + 1)));
    }

    @Test
    @DisplayName("Un codigo ya usado no se puede reutilizar")
    void codigoUsadoNoSirveDosVeces() {
        var code = new PasswordResetCode(1L, "hash", NOW);

        code.markUsed(NOW);

        assertTrue(code.isUsed());
        assertFalse(code.isUsable(NOW));
    }

    @Test
    @DisplayName("El codigo se invalida al agotar los intentos")
    void intentosAgotadosInvalidanElCodigo() {
        // Sin este limite, un codigo de seis digitos se adivina probando: son
        // solo un millon de combinaciones y un script las recorre en minutos.
        var code = new PasswordResetCode(1L, "hash", NOW);

        for (int i = 0; i < PasswordResetCode.MAX_ATTEMPTS - 1; i++) {
            code.registerFailedAttempt();
        }
        assertTrue(code.isUsable(NOW), "todavia deberia quedar un intento");

        code.registerFailedAttempt();

        assertFalse(code.hasAttemptsLeft());
        assertFalse(code.isUsable(NOW), "agotados los intentos, el codigo muere");
        assertEquals(PasswordResetCode.MAX_ATTEMPTS, code.getAttempts());
    }

    @Test
    @DisplayName("La ventana de vigencia es corta a proposito")
    void laVentanaDeVigenciaEsCorta() {
        assertTrue(PasswordResetCode.EXPIRATION_MINUTES <= 30,
                "un codigo de 6 digitos vigente por horas es adivinable");
        assertTrue(PasswordResetCode.MAX_ATTEMPTS <= 10,
                "demasiados intentos vuelven inutil el limite");
    }
}
