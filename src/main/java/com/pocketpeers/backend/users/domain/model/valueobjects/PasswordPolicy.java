package com.pocketpeers.backend.users.domain.model.valueobjects;

/**
 * Reglas minimas que debe cumplir una contrasena.
 *
 * <p>Se exige longitud y una mezcla de letras y digitos, pero deliberadamente no
 * se piden mayusculas ni simbolos. El publico objetivo del producto son personas
 * no bancarizadas, muchas con poca familiaridad digital: una politica exigente
 * las empuja a anotar la clave en un papel o a abandonar el registro, que es peor
 * para su seguridad que una contrasena algo mas simple pero recordable.</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    /**
     * Devuelve el motivo por el que la contrasena no es valida, o null si lo es.
     *
     * <p>Devuelve el motivo en lugar de un booleano para que la interfaz pueda
     * decirle a la persona que le falta, en vez de un "contrasena invalida" que no
     * ayuda a corregir nada.</p>
     */
    public static String validationError(String password) {
        if (password == null || password.isBlank()) {
            return "La contrasena no puede estar vacia";
        }
        if (password.length() < MIN_LENGTH) {
            return "La contrasena debe tener al menos " + MIN_LENGTH + " caracteres";
        }
        // BCrypt ignora todo lo que pase de 72 bytes: aceptar una clave mas larga
        // daria la falsa impresion de que los caracteres extra protegen algo.
        if (password.length() > MAX_LENGTH) {
            return "La contrasena no puede tener mas de " + MAX_LENGTH + " caracteres";
        }
        if (password.chars().noneMatch(Character::isLetter)) {
            return "La contrasena debe incluir al menos una letra";
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return "La contrasena debe incluir al menos un numero";
        }
        return null;
    }

    public static boolean isValid(String password) {
        return validationError(password) == null;
    }
}
