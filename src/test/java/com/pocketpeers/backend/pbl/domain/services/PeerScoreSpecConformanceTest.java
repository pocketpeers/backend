package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStats;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.OutcomeRecord;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.PaymentOutcome;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreParameters;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba que la implementacion corresponde con el documento
 * "Especificacion PeerScore - Backend" que esta en doc/.
 *
 * <p>Se separo de las pruebas de comportamiento a proposito. Aquellas verifican
 * que el motor haga lo correcto; estas verifican que haga lo <em>documentado</em>.
 * Si alguien cambia una constante sin actualizar la especificacion, o al reves,
 * esta clase lo detecta en vez de dejar que el documento y el codigo se separen en
 * silencio.</p>
 *
 * <p>Cada prueba cita la seccion del documento que verifica.</p>
 */
class PeerScoreSpecConformanceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);
    private static final long GROUP = 1L;

    /** Grupo con mediana S/50 y 70% de cumplimiento, usado en los calculos analiticos. */
    private static final GroupStats STATS = new GroupStats(BigDecimal.valueOf(50), 0.70, 1000);
    private static final Map<Long, GroupStats> GROUPS = Map.of(GROUP, STATS);

    private final PeerScoreCalculator calculator = new PeerScoreCalculator();

    // ------------------------------------------------------------------
    // Seccion 3.1 - Parametros
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3.1 - Los valores por defecto son los de la tabla de parametros")
    void parametrosPorDefectoCoincidenConLaEspecificacion() {
        ScoreParameters p = ScoreParameters.defaults();

        assertEquals(4.0, p.priorStrength(), 0.0, "prior-strength");
        assertEquals(0.35, p.counterpartyCap(), 0.0, "counterparty-cap");
        assertEquals(90.0, p.halfLifeDays(), 0.0, "half-life-days");
        assertEquals(3.0, p.maxEventWeight(), 0.0, "max-event-weight");
        assertEquals(0.80, p.reciprocityThreshold(), 0.0, "reciprocity-threshold");
        assertEquals(0.50, p.reciprocityPenalty(), 0.0, "reciprocity-penalty");

        assertEquals(25.0, p.bronzeScore(), 0.0, "umbral Bronce");
        assertEquals(60.0, p.silverScore(), 0.0, "umbral Plata");
        assertEquals(85.0, p.goldScore(), 0.0, "umbral Oro");

        assertEquals(18.0, p.silverMaxBandWidth(), 0.0, "ancho de banda Plata");
        assertEquals(12.0, p.goldMaxBandWidth(), 0.0, "ancho de banda Oro");

        assertEquals(2.0, p.bronzeMinCounterparties(), 0.0, "contrapartes Bronce");
        assertEquals(3.0, p.silverMinCounterparties(), 0.0, "contrapartes Plata");
        assertEquals(4.0, p.goldMinCounterparties(), 0.0, "contrapartes Oro");
    }

    // ------------------------------------------------------------------
    // Seccion 3.2 - Valor de resultado por evento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3.2 - Los valores v de cada desenlace son los documentados")
    void valoresDeResultadoCoincidenConLaEspecificacion() {
        assertEquals(1.0, PaymentOutcome.PUNTUAL.value(), 0.0);
        assertEquals(0.6, PaymentOutcome.PARCIAL_A_TIEMPO.value(), 0.0);
        assertEquals(0.3, PaymentOutcome.TARDIO.value(), 0.0);
        assertEquals(0.0, PaymentOutcome.VENCIDO.value(), 0.0);
    }

    // ------------------------------------------------------------------
    // Seccion 3.3 - Formulas, verificadas contra calculo analitico
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3.3 paso 1 - El peso por exposicion es 1 + ln(1 + monto/mediana)")
    void pesoPorExposicionSigueLaFormula() {
        // Un unico evento puntual, con monto igual a la mediana y sin decaimiento.
        // Con una sola contraparte no hay tope (tauEff = max(0.35, 1/1) = 1), asi
        // que el score se puede derivar a mano:
        //   w     = 1 + ln(1 + 50/50) = 1 + ln 2  = 1.6931472
        //   alpha = 4 * 0.70 + 1.6931472 * 1.0    = 4.4931472
        //   beta  = 4 * 0.30                      = 1.2
        //   score = 100 * alpha / (alpha + beta)  = 78.922
        double w = 1.0 + Math.log(2.0);
        double expected = 100.0 * (2.8 + w) / (2.8 + w + 1.2);

        double actual = score(List.of(event(10L, 50, PaymentOutcome.PUNTUAL, 0)));

        assertEquals(expected, actual, 0.001,
                "el peso no corresponde con 1 + ln(1 + monto/mediana)");
    }

    @Test
    @DisplayName("3.3 paso 1 - A los 90 dias exactos un evento pesa la mitad")
    void decaimientoAUnaVidaMediaEsExactamenteLaMitad() {
        //   w     = 1 + ln 2, delta = 2^(-90/90) = 0.5
        //   alpha = 2.8 + (1 + ln 2) * 0.5
        double w = 1.0 + Math.log(2.0);
        double expected = 100.0 * (2.8 + w * 0.5) / (2.8 + w * 0.5 + 1.2);

        double actual = score(List.of(event(10L, 50, PaymentOutcome.PUNTUAL, 90)));

        assertEquals(expected, actual, 0.001,
                "el decaimiento no corresponde con 2^(-dias/vidaMedia)");
    }

    @Test
    @DisplayName("3.3 paso 5 - Un vencido aporta al parametro beta, no al alpha")
    void vencidoAportaAlLadoDelIncumplimiento() {
        //   v = 0, asi que todo el peso va a beta:
        //   alpha = 2.8 ; beta = 1.2 + (1 + ln 2)
        double w = 1.0 + Math.log(2.0);
        double expected = 100.0 * 2.8 / (2.8 + 1.2 + w);

        double actual = score(List.of(event(10L, 50, PaymentOutcome.VENCIDO, 0)));

        assertEquals(expected, actual, 0.001);
    }

    @Test
    @DisplayName("3.3 - Sin historial el score es el prior del grupo, o sea 100 * tasa")
    void sinHistorialElScoreEsElPrior() {
        assertEquals(70.0, score(List.of()), 0.001);
    }

    @Test
    @DisplayName("3.3 paso 1 - El peso por monto respeta el tope maximo")
    void pesoPorMontoSeTopa() {
        // Un monto enorme deberia empujar el peso mas alla de 3, pero el tope lo
        // detiene. Con un solo evento puntual sin decaimiento:
        //   alpha = 2.8 + 3.0 ; beta = 1.2
        double expected = 100.0 * (2.8 + 3.0) / (2.8 + 3.0 + 1.2);

        double actual = score(List.of(event(10L, 5_000_000, PaymentOutcome.PUNTUAL, 0)));

        assertEquals(expected, actual, 0.001, "el peso no se topo en 3.0");
    }

    // ------------------------------------------------------------------
    // Seccion 3.5 - Resolucion de nivel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3.5 - Las tres condiciones de nivel son conjuntivas")
    void lasTresCondicionesDeNivelSonConjuntivas() {
        // Historial amplio y diverso: cumple puntaje, banda y contrapartes.
        ScoreResult completo = calculator.calculate(
                punctualHistory(80, 6), GROUPS, STATS, Map.of(), NOW);
        assertEquals(ReputationLevel.GOLD, completo.level(),
                "con puntaje, banda y diversidad suficientes deberia ser Oro. score="
                        + completo.score() + " banda=" + completo.bandWidth()
                        + " nEf=" + completo.effectiveCounterparties());

        // Mismo volumen de evidencia, pero concentrado en dos contrapartes:
        // el puntaje alcanza y la banda tambien, y aun asi no puede ser Oro.
        ScoreResult concentrado = calculator.calculate(
                punctualHistory(80, 2), GROUPS, STATS, Map.of(), NOW);
        assertTrue(concentrado.score() >= 85,
                "el puntaje deberia alcanzar: " + concentrado.score());
        assertTrue(concentrado.effectiveCounterparties() < 4,
                "contrapartes efectivas: " + concentrado.effectiveCounterparties());
        assertTrue(concentrado.level() != ReputationLevel.GOLD,
                "la diversidad insuficiente debe impedir el nivel Oro");
    }

    // ------------------------------------------------------------------
    // Seccion 4.1 - Restriccion de arquitectura
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4.1 - El calculador no depende de Spring ni de repositorios")
    void calculadorEsUnaFuncionPura() {
        for (Annotation annotation : PeerScoreCalculator.class.getAnnotations()) {
            String name = annotation.annotationType().getName();
            assertTrue(!name.startsWith("org.springframework"),
                    "el calculador quedo acoplado a Spring mediante " + name);
        }

        for (Field field : PeerScoreCalculator.class.getDeclaredFields()) {
            // Las constantes estaticas no son dependencias: lo que interesa es el
            // estado de instancia, que es donde se inyectarian repositorios.
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String type = field.getType().getName();
            assertTrue(type.equals(ScoreParameters.class.getName()),
                    "el calculador tiene un campo de instancia de tipo " + type
                            + "; solo deberia depender de sus parametros");
        }
    }

    @Test
    @DisplayName("4.1 - El instante de calculo entra como parametro, no del reloj del sistema")
    void elTiempoEntraComoParametro() {
        List<OutcomeRecord> outcomes = List.of(event(10L, 50, PaymentOutcome.PUNTUAL, 0));

        double enElMomento = calculator.calculate(outcomes, GROUPS, STATS, Map.of(), NOW).score();
        double unAnoDespues = calculator
                .calculate(outcomes, GROUPS, STATS, Map.of(), NOW.plusYears(1)).score();

        assertTrue(unAnoDespues < enElMomento,
                "mover el reloj deberia envejecer el historial; si no cambia, el "
                        + "calculador esta ignorando el parametro now");
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private double score(List<OutcomeRecord> outcomes) {
        return calculator.calculate(outcomes, GROUPS, STATS, Map.of(), NOW).score();
    }

    private OutcomeRecord event(long counterparty, double amount, PaymentOutcome outcome, long daysAgo) {
        return new OutcomeRecord(counterparty, GROUP, BigDecimal.valueOf(amount), outcome,
                NOW.minusDays(daysAgo));
    }

    private List<OutcomeRecord> punctualHistory(int count, int counterparties) {
        List<OutcomeRecord> outcomes = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            outcomes.add(event(10L + (i % counterparties), 50, PaymentOutcome.PUNTUAL, i % 60));
        }
        return outcomes;
    }
}
