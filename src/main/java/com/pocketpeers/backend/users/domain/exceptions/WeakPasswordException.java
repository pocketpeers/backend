package com.pocketpeers.backend.users.domain.exceptions;

/** La contrasena propuesta no cumple la politica minima. */
public class WeakPasswordException extends RuntimeException {
    public WeakPasswordException(String reason) {
        super(reason);
    }
}
