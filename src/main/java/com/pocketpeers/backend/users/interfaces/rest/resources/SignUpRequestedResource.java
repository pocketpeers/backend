package com.pocketpeers.backend.users.interfaces.rest.resources;

/**
 * Respuesta del primer paso del alta.
 *
 * @param verificationRequired si hace falta confirmar el correo con un codigo.
 *                             Cuando es {@code false} la cuenta ya quedo creada
 *                             y la aplicacion debe saltarse la pantalla del
 *                             codigo en vez de pedir uno que nadie envio.
 * @param message              texto que la aplicacion puede mostrar tal cual
 */
public record SignUpRequestedResource(boolean verificationRequired, String message) {
}
