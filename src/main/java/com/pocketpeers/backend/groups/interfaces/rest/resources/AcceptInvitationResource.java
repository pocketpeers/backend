package com.pocketpeers.backend.groups.interfaces.rest.resources;

/**
 * Lo mismo que se pide al entrar con codigo: aceptar la invitacion es entrar al
 * grupo, y entrar exige firmar la declaracion jurada.
 *
 * @param signatureImage firma dibujada en la aplicacion, PNG en base64.
 */
public record AcceptInvitationResource(String acceptedDeclarationVersion, String signatureImage) {
}
