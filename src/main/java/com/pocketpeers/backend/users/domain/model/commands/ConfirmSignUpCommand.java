package com.pocketpeers.backend.users.domain.model.commands;

/** Segundo paso del alta: el codigo que llego al correo. */
public record ConfirmSignUpCommand(String email, String code) {
}
