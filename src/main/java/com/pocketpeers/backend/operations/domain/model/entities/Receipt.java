package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.valueobjects.Amount;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Receipt extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private Amount amount;
    private String name;
    private String receiptNumber;
    private LocalDate issueDate;
    private String imagePath;
    private Boolean isActive = true;

    @OneToOne(mappedBy = "originalReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private OcrReceipt ocrReceipt;

    public Receipt(){};

    public Receipt(String name,Amount amount, LocalDate issueDate, String imagePath, String receiptNumber) {
        this.name = name;
        this.amount = amount;
        this.issueDate = issueDate;
        this.imagePath = imagePath;
        this.receiptNumber = receiptNumber;
    };

    public Receipt(String name,Amount amount, LocalDate issueDate, String imagePath) {
        this.name = name;
        this.amount = amount;
        this.issueDate = issueDate;
        this.imagePath = imagePath;
    };

}
