package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.configuration;

import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Pone al dia la restriccion CHECK del tipo de recordatorio en cada arranque.
 *
 * <p>Existe porque el proyecto usa {@code ddl-auto=update}, que crea el CHECK al
 * crear la tabla y no lo vuelve a tocar aunque el enum crezca.</p>
 *
 * <p><b>La lista se deriva del enum; no se escribe a mano.</b> Antes estaba
 * copiada aqui con cuatro valores, y cuando {@code OVERDUE_MANUAL} se agrego al
 * enum nadie actualizo la copia. El efecto fue peor que no tener esta clase: el
 * primer {@code DROP} si se ejecuta, asi que en cada arranque la restriccion
 * desaparecia y la nueva no llegaba a crearse, porque las filas con
 * {@code OVERDUE_MANUAL} ya la violaban. La tabla quedaba sin proteccion y el
 * unico rastro era un WARN entre el ruido del arranque. Derivandola del enum,
 * esa desincronizacion no puede repetirse.</p>
 */
@Component
public class PaymentReminderSchemaInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReminderSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public PaymentReminderSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void updateLegacyConstraints() {
        try {
            jdbcTemplate.execute("ALTER TABLE payment_reminders DROP CONSTRAINT IF EXISTS payment_reminders_type_check");
            jdbcTemplate.execute("ALTER TABLE payment_reminders DROP CONSTRAINT IF EXISTS uk_payment_reminder_payment_type");
            jdbcTemplate.execute("""
                    ALTER TABLE payment_reminders
                    ADD CONSTRAINT payment_reminders_type_check
                    CHECK (type IN (%s))
                    """.formatted(allowedTypes()));
        } catch (Exception exception) {
            // A nivel ERROR y diciendo como queda la tabla: si esto falla, el
            // DROP de arriba ya corrio y la columna se queda SIN restriccion,
            // que es justo el estado en el que un valor invalido entra sin que
            // nadie se entere.
            LOGGER.error("No se pudo recrear payment_reminders_type_check; la columna queda SIN restriccion. causa={}",
                    exception.getMessage(), exception);
        }
    }

    /** Los valores del enum, entrecomillados y separados por comas. */
    private static String allowedTypes() {
        return Arrays.stream(PaymentReminderType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
