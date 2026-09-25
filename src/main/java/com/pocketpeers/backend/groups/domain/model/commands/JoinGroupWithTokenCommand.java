package com.pocketpeers.backend.groups.domain.model.commands;

/**
 * @param acceptedDeclarationVersion version de la declaracion jurada que el
 *                                   usuario acepto antes de unirse; sin ella no
 *                                   se entra al grupo.
 * @param signatureImage             firma dibujada en la aplicacion, PNG en base64.
 */
public record JoinGroupWithTokenCommand(
        Long groupId,
        String token,       
        Long userId,
        String acceptedDeclarationVersion,
        String signatureImage
) {}
