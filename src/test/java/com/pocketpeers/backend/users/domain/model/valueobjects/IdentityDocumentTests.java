package com.pocketpeers.backend.users.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdentityDocumentTests {

    @Test
    void dniAcceptsEightDigitsAndRejectsAnythingElse() {
        assertThat(IdentityDocument.of("DNI", "12345678").number()).isEqualTo("12345678");

        assertThatThrownBy(() -> IdentityDocument.of("DNI", "1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El DNI debe tener exactamente 8 digitos");

        assertThatThrownBy(() -> IdentityDocument.of("DNI", "1234567A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El DNI debe tener exactamente 8 digitos");
    }

    /**
     * La normalizacion es lo que hace util la restriccion de unicidad: sin ella,
     * la misma persona se registraria dos veces escribiendo su documento con un
     * guion o con un espacio de mas.
     */
    @Test
    void normalizesBeforeStoringSoTheSameDocumentCannotSlipInTwice() {
        var plain = IdentityDocument.of("DNI", "12345678");
        var spaced = IdentityDocument.of("DNI", " 1234 5678 ");
        var hyphenated = IdentityDocument.of("dni", "1234-5678");

        assertThat(spaced).isEqualTo(plain);
        assertThat(hyphenated).isEqualTo(plain);
    }

    @Test
    void passportAndForeignCardKeepTheirOwnFormats() {
        assertThat(IdentityDocument.of("PASAPORTE", "ab123456").number()).isEqualTo("AB123456");
        assertThat(IdentityDocument.of("CE", "001234567").type()).isEqualTo(DocumentType.CE);

        assertThatThrownBy(() -> IdentityDocument.of("PASAPORTE", "AB12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El pasaporte debe tener entre 6 y 12 caracteres alfanumericos");
    }

    /**
     * El carne de extranjeria es estrictamente numerico, de nueve o diez digitos.
     *
     * <p>Antes admitia de 8 a 12 caracteres alfanumericos, y con eso pasaban
     * numeros que no existen. La prueba fija el rango por los dos extremos y
     * tambien por el alfabeto, que es donde se aflojaria sin querer: un rango
     * comprobado solo por arriba vuelve a aceptar ocho digitos en cuanto
     * alguien toque la expresion.</p>
     */
    @Test
    void foreignCardAcceptsOnlyNineOrTenDigits() {
        assertThat(IdentityDocument.of("CE", "001234567").number()).isEqualTo("001234567");
        assertThat(IdentityDocument.of("CE", "0012345678").number()).isEqualTo("0012345678");

        for (var invalid : new String[]{"00123456", "00123456789", "AB1234567"}) {
            assertThatThrownBy(() -> IdentityDocument.of("CE", invalid))
                    .as("CE invalido: %s", invalid)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El carne de extranjeria debe tener 9 o 10 digitos");
        }
    }

    /**
     * Un DNI y un pasaporte pueden coincidir en digitos sin ser la misma
     * persona, asi que el tipo forma parte de la identidad del documento.
     */
    @Test
    void sameNumberWithDifferentTypeIsADifferentDocument() {
        assertThat(IdentityDocument.of("DNI", "12345678"))
                .isNotEqualTo(IdentityDocument.of("PASAPORTE", "12345678"));
    }

    @Test
    void rejectsMissingValuesWithAMessageThatNamesTheValidTypes() {
        assertThatThrownBy(() -> IdentityDocument.of("LIBRETA", "12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNI")
                .hasMessageContaining("PASAPORTE");

        assertThatThrownBy(() -> IdentityDocument.of(null, "12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatorio");

        assertThatThrownBy(() -> IdentityDocument.of("DNI", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El numero de documento es obligatorio");
    }
}
