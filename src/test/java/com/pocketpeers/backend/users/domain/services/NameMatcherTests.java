package com.pocketpeers.backend.users.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.users.domain.model.valueobjects.OfficialName;
import com.pocketpeers.backend.users.domain.services.NameMatcher.Result;
import org.junit.jupiter.api.Test;

/**
 * La regla de coincidencia entre el nombre escrito y el del DNI.
 *
 * <p>Basta un nombre y un apellido, pero todo lo escrito tiene que ser de esa
 * persona.</p>
 */
class NameMatcherTests {

    private static final OfficialName ROSA = new OfficialName("ROSA MARIA", "QUISPE", "HUAMAN");

    @Test
    void oneGivenNameAndOneLastNameIsEnough() {
        assertThat(NameMatcher.compare("Rosa", "Quispe", ROSA)).isEqualTo(Result.PARTIAL);
    }

    @Test
    void theSecondGivenNameAndTheMothersLastNameAlsoCount() {
        // Hay quien usa el segundo nombre o el apellido materno.
        assertThat(NameMatcher.compare("María", "Huamán", ROSA)).isEqualTo(Result.PARTIAL);
    }

    @Test
    void writingTheWholeNameIsAFullMatch() {
        assertThat(NameMatcher.compare("Rosa María", "Quispe Huamán", ROSA)).isEqualTo(Result.FULL);
    }

    @Test
    void orderDoesNotMatter() {
        assertThat(NameMatcher.compare("María Rosa", "Huamán Quispe", ROSA)).isEqualTo(Result.FULL);
    }

    @Test
    void aWordThatIsNotInTheDocumentFailsEvenIfTheRestMatches() {
        // Sin esto, cualquiera completaria un DNI ajeno con su propio apellido.
        assertThat(NameMatcher.compare("Rosa", "Quispe Flores", ROSA)).isEqualTo(Result.MISMATCH);
    }

    @Test
    void typosAreNotTolerated() {
        assertThat(NameMatcher.compare("Rossa", "Quispe", ROSA)).isEqualTo(Result.MISMATCH);
    }

    @Test
    void accentsCaseAndExtraSpacesAreIgnored() {
        var jose = new OfficialName("JOSE", "PEREZ", "RUIZ");
        assertThat(NameMatcher.compare("  josé ", "  PÉREZ  ", jose)).isEqualTo(Result.PARTIAL);
    }

    @Test
    void theEnyeMatchesWithOrWithoutTilde() {
        var pena = new OfficialName("LUIS", "PEÑA", "SOTO");
        assertThat(NameMatcher.compare("Luis", "Peña", pena)).isEqualTo(Result.PARTIAL);
        assertThat(NameMatcher.compare("Luis", "Pena", pena)).isEqualTo(Result.PARTIAL);
    }

    @Test
    void particlesDoNotIdentifyAnyone() {
        var cruz = new OfficialName("ANA", "DE LA CRUZ", "TORRES");
        assertThat(NameMatcher.compare("Ana", "De la Cruz", cruz)).isEqualTo(Result.PARTIAL);
        assertThat(NameMatcher.compare("Ana", "Cruz Torres", cruz)).isEqualTo(Result.FULL);
    }

    @Test
    void aFieldWithOnlyParticlesDoesNotCountAsALastName() {
        var cruz = new OfficialName("ANA", "DE LA CRUZ", "TORRES");
        assertThat(NameMatcher.compare("Ana", "De la", cruz)).isEqualTo(Result.MISMATCH);
    }

    @Test
    void hyphenatedNamesAreSplitIntoWords() {
        var ruiz = new OfficialName("CARLOS", "PEREZ", "RUIZ");
        assertThat(NameMatcher.compare("Carlos", "Pérez-Ruiz", ruiz)).isEqualTo(Result.FULL);
    }

    @Test
    void aDocumentWithoutMothersLastNameCanStillBeAFullMatch() {
        var single = new OfficialName("PEDRO", "GARCIA", "");
        assertThat(NameMatcher.compare("Pedro", "García", single)).isEqualTo(Result.FULL);
    }

    @Test
    void anEmptyFieldIsAMismatch() {
        assertThat(NameMatcher.compare("", "Quispe", ROSA)).isEqualTo(Result.MISMATCH);
        assertThat(NameMatcher.compare("Rosa", null, ROSA)).isEqualTo(Result.MISMATCH);
    }

    @Test
    void aLastNameWrittenInTheGivenNameFieldDoesNotMatch() {
        // La comparacion respeta los campos: QUISPE no es un nombre de pila.
        assertThat(NameMatcher.compare("Rosa Quispe", "Huamán", ROSA)).isEqualTo(Result.MISMATCH);
    }
}
