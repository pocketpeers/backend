package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
                    CHECK (type IN (
                        'DUE_IN_48_HOURS',
                        'DUE_TODAY',
                        'EXPENSE_ASSIGNED',
                        'PAYMENT_REGISTERED'
                    ))
                    """);
        } catch (Exception exception) {
            LOGGER.warn("Could not update payment reminder constraints: {}", exception.getMessage());
        }
    }
}
