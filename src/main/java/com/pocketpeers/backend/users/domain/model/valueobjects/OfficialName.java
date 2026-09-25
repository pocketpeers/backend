package com.pocketpeers.backend.users.domain.model.valueobjects;

/**
 * Nombre que corresponde a un DNI segun la fuente externa.
 *
 * <p>Vive solo en memoria mientras dura la verificacion; no se persiste.</p>
 *
 * @param givenNames     los nombres de pila, todos juntos ("ROSA MARIA")
 * @param firstLastName  apellido paterno
 * @param secondLastName apellido materno; puede venir vacio
 */
public record OfficialName(String givenNames, String firstLastName, String secondLastName) {
}
