package com.pocketpeers.backend.users.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Documento de identidad de quien se registra.
 *
 * <p>Se pide para poder acreditar, en el estudio de campo, que detras de cada
 * cuenta hay una persona distinta. El par tipo + numero es unico en la base:
 * esa restriccion es lo que de verdad impide dos cuentas para la misma persona,
 * porque la comprobacion en codigo no sobrevive a dos registros simultaneos.</p>
 *
 * <p>Ojo con lo que este campo NO hace: nadie contrasta el numero contra RENIEC,
 * asi que prueba que no hay duplicados, no que el documento exista. Para
 * afirmar lo segundo haria falta una verificacion externa.</p>
 */
@Embeddable
public record IdentityDocument(
        @Enumerated(EnumType.STRING)
        @Column(name = "document_type", length = 16)
        DocumentType type,

        @Column(name = "document_number", length = 16)
        String number
) {
    public IdentityDocument {
        if (type == null) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio");
        }
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("El numero de documento es obligatorio");
        }

        // Se normaliza antes de validar y de guardar. Si no, "12345678" y
        // "1234-5678 " serian dos filas distintas para la restriccion de
        // unicidad y la misma persona podria registrarse dos veces separando
        // los digitos de otra forma.
        number = number.trim().toUpperCase().replaceAll("[\\s.-]", "");

        if (!type.matches(number)) {
            throw new IllegalArgumentException(type.validationMessage());
        }
    }

    /** Construye desde el texto que llega de la API, validando el tipo con un mensaje util. */
    public static IdentityDocument of(String type, String number) {
        return new IdentityDocument(DocumentType.fromString(type), number);
    }
}
