package com.pocketpeers.backend.users.domain.services;

import com.pocketpeers.backend.users.domain.model.valueobjects.OfficialName;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide si el nombre escrito en el registro corresponde al del documento.
 *
 * <p>La regla es deliberadamente asimetrica. La aplicacion no obliga a escribir
 * el nombre completo, asi que basta <b>un nombre y un apellido</b>; pero
 * <b>todo lo que se escriba tiene que estar en el documento</b>. "Rosa Quispe"
 * pasa para ROSA MARIA QUISPE HUAMAN; "Rosa Quispe Flores" no, porque FLORES no
 * es de esa persona. Sin la segunda mitad, cualquiera podria completar un DNI
 * ajeno con su propio apellido y pasar la verificacion.</p>
 *
 * <p>Los errores de tipeo no se toleran. Aceptar "Rossa" por "Rosa" abriria la
 * puerta a coincidencias falsas, y los pocos casos legitimos que caen aqui se
 * resuelven en la sesion presencial, contra el consentimiento firmado.</p>
 *
 * <p>Es logica pura, sin dependencias, para poder probarla caso por caso.</p>
 */
public final class NameMatcher {

    /** Particulas que no identifican a nadie: "De la Cruz" se compara como CRUZ. */
    private static final Set<String> PARTICLES = Set.of("DE", "DEL", "LA", "LAS", "LOS", "Y");

    public enum Result { FULL, PARTIAL, MISMATCH }

    private NameMatcher() {
    }

    /**
     * @param firstName lo que la persona escribio como nombre
     * @param lastName  lo que la persona escribio como apellido
     * @param official  el nombre que corresponde al documento
     */
    public static Result compare(String firstName, String lastName, OfficialName official) {
        var writtenGiven = tokens(firstName);
        var writtenLast = tokens(lastName);
        // Minimo un nombre y un apellido. Un campo que solo trae particulas
        // ("De") no identifica a nadie y se trata como vacio.
        if (writtenGiven.isEmpty() || writtenLast.isEmpty()) {
            return Result.MISMATCH;
        }

        var officialGiven = tokens(official.givenNames());
        // Vale el apellido paterno o el materno: hay quien usa el de la madre.
        var officialLast = new HashSet<>(tokens(official.firstLastName()));
        officialLast.addAll(tokens(official.secondLastName()));

        if (!officialGiven.containsAll(writtenGiven) || !officialLast.containsAll(writtenLast)) {
            return Result.MISMATCH;
        }
        return writtenGiven.containsAll(officialGiven) && writtenLast.containsAll(officialLast)
                ? Result.FULL
                : Result.PARTIAL;
    }

    /**
     * Palabras significativas de un nombre, normalizadas.
     *
     * <p>Mayusculas y sin tildes, para que José y JOSE sean lo mismo. La Ñ queda
     * como N en ambos lados: muchos teclados no la tienen a mano, y como la
     * normalizacion es la misma para lo escrito y para el documento, PEÑA y
     * PENA coinciden sin que eso abra coincidencias con otro apellido real.</p>
     */
    static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        var withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return Arrays.stream(withoutAccents.toUpperCase(Locale.ROOT)
                        // Guiones, apostrofes y puntos separan palabras: "Pérez-Ruiz" son dos.
                        .replaceAll("[^A-Z]+", " ")
                        .trim()
                        .split(" "))
                .filter(token -> !token.isEmpty())
                .filter(token -> !PARTICLES.contains(token))
                .collect(Collectors.toSet());
    }
}
