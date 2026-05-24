    package com.pocketpeers.backend.groups.domain.model.commands;

    public record CreateGroupCommand(String name, String groupPhoto, String description, Long adminId) {
        public CreateGroupCommand {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Group name cannot be null or empty");
            }

            if (adminId == null || adminId < 0) {
                throw new IllegalArgumentException("Admin id cannot be negative");
            }
        }

    }
