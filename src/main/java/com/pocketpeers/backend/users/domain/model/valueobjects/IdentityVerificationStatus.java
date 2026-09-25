package com.pocketpeers.backend.users.domain.model.valueobjects;

/**
 * Resultado de contrastar el nombre registrado contra el que corresponde al DNI.
 *
 * <p>No hay un estado "no coincide": un alta cuyo nombre no coincide se rechaza
 * antes de enviar el codigo al correo, asi que nunca llega a existir una cuenta
 * con ese resultado.</p>
 */
public enum IdentityVerificationStatus {

    /** El nombre registrado incluye los nombres y los dos apellidos del documento. */
    FULL_MATCH,

    /**
     * Coincide al menos un nombre y un apellido, y nada de lo escrito es ajeno
     * al documento. Es lo normal: la aplicacion no exige el nombre completo.
     */
    PARTIAL_MATCH,

    /**
     * No se pudo verificar en el alta —el servicio externo no respondio, no
     * encontro el documento, se alcanzo el tope diario o la verificacion estaba
     * apagada— y queda para revisarse despues. Nunca bloquea el registro.
     */
    PENDING,

    /** Documento que el servicio externo no cubre: carne de extranjeria y pasaporte. */
    NOT_APPLICABLE
}
