package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.Amount;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;


@Getter
@Setter
@Entity
@PrimaryKeyJoinColumn(name = "receipt_id")
public class PaymentReceipt extends Receipt{
    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    public PaymentReceipt() {}

    public PaymentReceipt(String name, Amount amount, LocalDate issueDate, String imagePath, Payment payment) {
        super(name, amount, issueDate, imagePath);
        this.payment = payment;
    }
    public PaymentReceipt(String name,String receiptNumber, Amount amount, LocalDate issueDate, String imagePath, Payment payment) {
        super(name, amount, issueDate, imagePath,receiptNumber);
        this.payment = payment;
    }
    public void assignToPayment(Payment payment){
        this.payment = payment;
    }

}
