package com.pocketpeers.backend.users.domain.model.valueobjects;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Tipo de documento de identidad admitido en el registro.
 *
 * <p>Cada tipo trae su propio formato porque validarlos todos con una regla
 * comun no serviria de nada: el patron mas permisivo —el del pasaporte— dejaria
 * pasar cualquier DNI mal escrito, que es justo el error que se quiere evitar.</p>
 */
public enum DocumentType {
    /** Documento Nacional de Identidad peruano: ocho digitos, siempre. */
    DNI("\\d{8}", "El DNI debe tener exactamente 8 digitos"),

    /** Carne de Extranjeria. Los formatos antiguos y los actuales conviven, de ahi el rango. */
    CE("[A-Z0-9]{8,12}", "El carne de extranjeria debe tener entre 8 y 12 caracteres alfanumericos"),

    /** Pasaporte, de cualquier pais. El formato lo fija cada emisor, asi que solo se acota la longitud. */
    PASAPORTE("[A-Z0-9]{6,12}", "El pasaporte debe tener entre 6 y 12 caracteres alfanumericos");

    private final Pattern pattern;
    private final String validationMessage;

    DocumentType(String regex, String validationMessage) {
        this.pattern = Pattern.compile(regex);
        this.validationMessage = validationMessage;
    }

    public boolean matches(String number) {
        return pattern.matcher(number).matches();
    }

    public String validationMessage() {
        return validationMessage;
    }

    /**
     * Convierte el texto que llega de la API.
     *
     * <p>No se usa {@code valueOf} directamente porque su excepcion solo dice
     * "No enum constant ...", sin nombrar las opciones validas: quien integra la
     * app movil tendria que abrir el codigo para averiguar que puede mandar.</p>
     */
    public static DocumentType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio. Valores validos: "
                    + Arrays.toString(values()));
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento no valido: '" + value
                        + "'. Valores validos: " + Arrays.toString(values())));
    }
}
