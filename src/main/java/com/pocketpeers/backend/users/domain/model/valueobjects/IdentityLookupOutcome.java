package com.pocketpeers.backend.users.domain.model.valueobjects;

/** Como termino un intento de verificacion de DNI en el registro. */
public enum IdentityLookupOutcome {
    /** El nombre coincide con el documento. */
    MATCH,
    /** El nombre no coincide: el alta se rechazo. */
    MISMATCH,
    /** La fuente externa no encontro el documento; la cuenta queda pendiente. */
    NOT_FOUND,
    /** La fuente externa no respondio; la cuenta queda pendiente. */
    UNAVAILABLE,
    /** Se alcanzo el tope diario global: no se consulto y la cuenta queda pendiente. */
    DAILY_CAP,
    /** El dispositivo o la IP agotaron sus intentos: el alta se rechazo. */
    LIMITED
}
