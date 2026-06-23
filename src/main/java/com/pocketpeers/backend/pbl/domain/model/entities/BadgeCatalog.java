package com.pocketpeers.backend.pbl.domain.model.entities;

import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
public class BadgeCatalog extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;


    public BadgeCatalog() {
    }

    public BadgeCatalog(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
}
