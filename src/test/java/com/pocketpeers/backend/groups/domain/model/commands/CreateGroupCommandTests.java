package com.pocketpeers.backend.groups.domain.model.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CreateGroupCommandTests {

    @Test
    void acceptsValidCommand() {
        CreateGroupCommand command = new CreateGroupCommand("Casa", "photo.png", "Gastos", 1L);

        assertThat(command.name()).isEqualTo("Casa");
        assertThat(command.adminId()).isEqualTo(1L);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new CreateGroupCommand(" ", "photo.png", "Gastos", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Group name cannot be null or empty");
    }

    @Test
    void rejectsMissingOrNegativeAdminId() {
        assertThatThrownBy(() -> new CreateGroupCommand("Casa", "photo.png", "Gastos", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin id cannot be negative");

        assertThatThrownBy(() -> new CreateGroupCommand("Casa", "photo.png", "Gastos", -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin id cannot be negative");
    }
}
