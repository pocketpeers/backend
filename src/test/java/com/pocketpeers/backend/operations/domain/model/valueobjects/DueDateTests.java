package com.pocketpeers.backend.operations.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DueDateTests {

    @Test
    void rejectsNullDueDate() {
        assertThatThrownBy(() -> new DueDate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Due date cannot be null");
    }

    @Test
    void detectsOverdueDates() {
        DueDate dueDate = new DueDate(LocalDate.now().minusDays(1));

        assertThat(dueDate.isOverdue()).isTrue();
        assertThat(dueDate.isDueSoon()).isTrue();
    }

    @Test
    void calculatesRemainingDaysForFutureDates() {
        DueDate dueDate = new DueDate(LocalDate.now().plusDays(3));

        assertThat(dueDate.isOverdue()).isFalse();
        assertThat(dueDate.daysRemaining()).isEqualTo(3);
    }
}
