package com.pocketpeers.backend.operations.domain.model.valueobjects;

import java.time.LocalDate;

public record DueDate(LocalDate dueDate) {

    public DueDate {
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date cannot be null");
        }
        /*if (dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }*/
    }

    public boolean isDueSoon() {
        return dueDate.minusDays(1).isBefore(LocalDate.now());
    }

    public boolean isOverdue() {
        return LocalDate.now().isAfter(this.dueDate);
    }

    public long daysRemaining() {
        return LocalDate.now().until(dueDate).getDays();
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}

