package com.pocketpeers.backend.operations.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record ExpenseName(String name) {

    public ExpenseName() {this(null);}

    public ExpenseName{
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Expense name cannot be null or blank");
        }
    }

    public String getName(){
        return name;
    }
}
