package com.pocketpeers.backend.groups.interfaces.rest.resources;

public record CreateGroupResource(String name, String groupPhoto, String description, Long adminId,
                                  String acceptedDeclarationVersion, String signatureImage) {
}
