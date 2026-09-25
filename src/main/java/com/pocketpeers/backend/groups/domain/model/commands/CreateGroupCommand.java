    package com.pocketpeers.backend.groups.domain.model.commands;

    /**
     * @param acceptedDeclarationVersion version de la declaracion jurada que el
     *                                   creador acepto: el administrador tambien
     *                                   es miembro y registra movimientos, asi
     *                                   que firma igual que los demas.
     * @param signatureImage             firma dibujada en la aplicacion, PNG en base64.
     */
    public record CreateGroupCommand(String name, String groupPhoto, String description, Long adminId,
                                     String acceptedDeclarationVersion, String signatureImage) {
        public CreateGroupCommand {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Group name cannot be null or empty");
            }

            if (adminId == null || adminId < 0) {
                throw new IllegalArgumentException("Admin id cannot be negative");
            }
        }

        public CreateGroupCommand(String name, String groupPhoto, String description, Long adminId) {
            this(name, groupPhoto, description, adminId, null, null);
        }

    }
