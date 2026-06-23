package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class PaymentEvidence extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private String photo;

    public PaymentEvidence() {}

    public PaymentEvidence(Payment payment, String photo) {
        this.payment = payment;
        this.photo = photo;
    }

    public void assignToPayment(Payment payment) {
        this.payment = payment;
    }
}
