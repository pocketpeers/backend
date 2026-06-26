package com.pocketpeers.backend.operations.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentEvidence;
import org.junit.jupiter.api.Test;

class PaymentTests {

    @Test
    void startsPendingAndUnpaid() {
        Payment payment = new Payment("Cena", new BigDecimal("100.00"), null, null);

        assertThat(payment.getStatus()).isEqualTo("PENDING");
        assertThat(payment.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(payment.getConfirmed()).isFalse();
    }

    @Test
    void partialPaymentUpdatesAmountAndStatus() {
        Payment payment = new Payment("Cena", new BigDecimal("100.00"), null, null);

        payment.pay(new BigDecimal("40.00"));

        assertThat(payment.getAmountPaid()).isEqualByComparingTo("40.00");
        assertThat(payment.getStatus()).isEqualTo("PARTIAL");
        assertThat(payment.getConfirmed()).isFalse();
    }

    @Test
    void fullPaymentMarksPaymentCompletedUntilConfirmation() {
        Payment payment = new Payment("Cena", new BigDecimal("100.00"), null, null);

        payment.pay(new BigDecimal("100.00"));

        assertThat(payment.getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getConfirmed()).isFalse();
    }

    @Test
    void rejectsPaymentsAboveTotalAmount() {
        Payment payment = new Payment("Cena", new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> payment.pay(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Partial payment cannot exceed total amount");
    }

    @Test
    void blocksChangesAfterCompletedPaymentIsConfirmed() {
        Payment payment = new Payment("Cena", new BigDecimal("100.00"), null, null);
        payment.pay(new BigDecimal("100.00"));
        payment.confirmPayment();

        assertThatThrownBy(() -> payment.pay(new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Completed confirmed payments cannot be modified");
    }

    @Test
    void addEvidenceLinksBothSides() {
        Payment payment = new Payment("Cena", new BigDecimal("100.00"), null, null);
        PaymentEvidence evidence = new PaymentEvidence(null, "receipt.jpg");

        payment.addEvidence(evidence);

        assertThat(payment.getEvidences()).containsExactly(evidence);
        assertThat(evidence.getPayment()).isSameAs(payment);
    }
}
