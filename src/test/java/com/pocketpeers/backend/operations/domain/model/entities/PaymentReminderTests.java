package com.pocketpeers.backend.operations.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;

class PaymentReminderTests {

    @Test
    void startsUnreadAndCanBeMarkedRead() {
        User user = new User("ana", "secret");
        Payment payment = new Payment("Cena", new BigDecimal("50.00"), user, null);
        PaymentReminder reminder = new PaymentReminder(user, payment, PaymentReminderType.DUE_TODAY, "Pago pendiente", "Tienes un pago");

        assertThat(reminder.isRead()).isFalse();
        assertThat(reminder.getUser()).isSameAs(user);
        assertThat(reminder.getPayment()).isSameAs(payment);
        assertThat(reminder.getType()).isEqualTo(PaymentReminderType.DUE_TODAY);

        reminder.markRead();

        assertThat(reminder.isRead()).isTrue();
        assertThat(reminder.getReadAt()).isNotNull();
    }
}
