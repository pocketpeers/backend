package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.valueobjects.ReceiptPerceptualHash;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptDuplicateDetectorTests {

    private static final long BASE = 0x0F0F0F0F0F0F0F0FL;

    @Test
    void sinRegistrosNoHaySospecha() {
        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(BASE, Collections.emptyList())).isEmpty();
    }

    @Test
    void huellaIdenticaLevantaSospecha() {
        var registrada = new ReceiptPerceptualHash(10L, 100L, BASE);

        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(BASE, List.of(registrada)))
                .contains(registrada);
    }

    @Test
    void dentroDelUmbralLevantaSospecha() {
        long casiIgual = flipBits(BASE, ReceiptDuplicateDetector.SUSPICION_HAMMING_DISTANCE);

        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(
                casiIgual, List.of(new ReceiptPerceptualHash(10L, 100L, BASE))))
                .isPresent();
    }

    @Test
    void justoFueraDelUmbralNoLevantaSospecha() {
        long distinta = flipBits(BASE, ReceiptDuplicateDetector.SUSPICION_HAMMING_DISTANCE + 1);

        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(
                distinta, List.of(new ReceiptPerceptualHash(10L, 100L, BASE))))
                .isEmpty();
    }

    /**
     * Con varios candidatos por debajo del umbral hay que senalar el mas
     * parecido, no el primero que devolvio la consulta: si no, el mensaje de
     * error apuntaria a un gasto distinto segun el orden de las filas.
     */
    @Test
    void entreVariosDevuelveElMasParecido() {
        var lejano = new ReceiptPerceptualHash(1L, 100L, flipBits(BASE, 4));
        var cercano = new ReceiptPerceptualHash(2L, 200L, flipBits(BASE, 1));

        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(BASE, Arrays.asList(lejano, cercano)))
                .contains(cercano);
    }

    @Test
    void ignoraRegistrosSinHuella() {
        var sinHuella = new ReceiptPerceptualHash(1L, 100L, null);

        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(BASE, List.of(sinHuella))).isEmpty();
    }

    @Test
    void elUmbralEsConfigurableParaLosExperimentos() {
        long distinta = flipBits(BASE, 12);
        var registrada = List.of(new ReceiptPerceptualHash(1L, 100L, BASE));

        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(distinta, registrada, 4)).isEmpty();
        assertThat(ReceiptDuplicateDetector.findSuspectedDuplicate(distinta, registrada, 20)).isPresent();
    }

    private static long flipBits(long value, int howMany) {
        long flipped = value;
        for (int bit = 0; bit < howMany; bit++) {
            flipped ^= (1L << bit);
        }
        return flipped;
    }
}
