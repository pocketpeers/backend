package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStats;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.NextLevelGoal;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.OutcomeRecord;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.PaymentOutcome;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreParameters;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica las propiedades que PeerScore debe cumplir.
 *
 * <p>No son pruebas de que el codigo "corre": cada una comprueba una propiedad
 * que el motor anterior no tenia y que hay que poder demostrar. Todas se ejecutan
 * sin base de datos y sin contexto de Spring, que es la razon por la que el
 * calculador es una funcion pura.</p>
 */
class PeerScoreCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);
    private static final long GROUP = 1L;

    /** Grupo de referencia: mediana S/50 y 70% de cumplimiento historico. */
    private static final GroupStats GLOBAL = new GroupStats(BigDecimal.valueOf(50), 0.70, 1000);
    private static final Map<Long, GroupStats> GROUPS = Map.of(GROUP, GLOBAL);

    private final PeerScoreCalculator calculator = new PeerScoreCalculator();

    // ------------------------------------------------------------------
    // Resistencia a manipulacion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una sola contraparte no pasa de Nuevo, por mas pagos que se registren")
    void unaSolaContraparteNoSuperaNuevo() {
        List<OutcomeRecord> outcomes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            outcomes.add(event(99L, 50, PaymentOutcome.PUNTUAL, i));
        }

        ScoreResult result = calculate(outcomes);

        assertTrue(result.score() > 90,
                "el puntaje sube, porque los pagos existen: " + result.score());
        assertEquals(ReputationLevel.NEW, result.level(),
                "pero el nivel no, porque una contraparte no alcanza la diversidad minima");
        assertTrue(result.effectiveCounterparties() < 1.01,
                "contrapartes efectivas: " + result.effectiveCounterparties());
    }

    @Test
    @DisplayName("Un anillo de tres cuentas no pasa de Bronce")
    void anilloDeTresNoSuperaBronce() {
        List<OutcomeRecord> outcomes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            outcomes.add(event(i % 2 == 0 ? 98L : 99L, 50, PaymentOutcome.PUNTUAL, i));
        }

        ScoreResult result = calculate(outcomes);

        assertTrue(result.score() > 90, "puntaje: " + result.score());
        assertEquals(ReputationLevel.BRONZE, result.level(),
                "dos contrapartes no alcanzan las tres que exige Plata");
    }

    @Test
    @DisplayName("Dos contrapartes equilibradas alcanzan el minimo de diversidad de Bronce")
    void dosContrapartesEquilibradasAlcanzanElMinimo() {
        // Prueba de regresion. El bucle de topes converge de forma geometrica, asi
        // que dos contrapartes perfectamente equilibradas dan 1.99999999997 en vez
        // de 2.0 exacto. Sin tolerancia en la comparacion de umbrales, esta persona
        // se quedaba en Nuevo por un residuo de 2.8e-11 que no tiene nada que ver
        // con su conducta.
        List<OutcomeRecord> outcomes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            outcomes.add(event(i % 2 == 0 ? 98L : 99L, 50, PaymentOutcome.PUNTUAL, i));
        }

        ScoreResult result = calculate(outcomes);

        assertTrue(result.effectiveCounterparties() < 2.0,
                "el residuo de punto flotante existe: " + result.effectiveCounterparties());
        assertEquals(ReputationLevel.BRONZE, result.level(),
                "pero no debe costarle el nivel");
    }

    @Test
    @DisplayName("La reciprocidad total descuenta la evidencia del par")
    void reciprocidadTotalDescuentaEvidencia() {
        List<OutcomeRecord> outcomes = List.of(
                event(10L, 50, PaymentOutcome.PUNTUAL, 1),
                event(10L, 50, PaymentOutcome.PUNTUAL, 5),
                event(11L, 50, PaymentOutcome.PUNTUAL, 9),
                event(12L, 50, PaymentOutcome.PUNTUAL, 12));

        ScoreResult sinReciprocidad = calculate(outcomes);

        // La contraparte 10 devuelve exactamente la misma evidencia en sentido
        // inverso: se pagan mutuamente en la misma medida.
        Map<Long, Double> reverse = new HashMap<>();
        reverse.put(10L, evidenceFor(outcomes, 10L));
        ScoreResult conReciprocidad = calculator.calculate(outcomes, GROUPS, GLOBAL, reverse, NOW);

        assertTrue(conReciprocidad.score() < sinReciprocidad.score(),
                "con reciprocidad " + conReciprocidad.score()
                        + " deberia ser menor que sin ella " + sinReciprocidad.score());
    }

    @Test
    @DisplayName("El tope por contraparte no colapsa la evidencia cuando hay solo dos")
    void topeNoColapsaConDosContrapartes() {
        // Prueba de regresion. Con tope 0.35 y dos contrapartes la restriccion
        // "cada una aporta como maximo el 35%" es imposible de satisfacer entre
        // dos. Sin el piso 1/n, cada iteracion recorta mas y la evidencia tiende
        // a cero: el score caeria al prior del grupo, o sea 70.
        List<OutcomeRecord> outcomes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            outcomes.add(event(i % 2 == 0 ? 10L : 11L, 50, PaymentOutcome.PUNTUAL, i));
        }

        ScoreResult result = calculate(outcomes);

        assertTrue(result.score() > 85,
                "la evidencia colapso: el score cayo a " + result.score()
                        + ", cerca del prior 70, en vez de reflejar diez pagos puntuales");
        assertTrue(result.effectiveCounterparties() > 1.9,
                "contrapartes efectivas: " + result.effectiveCounterparties());
    }

    // ------------------------------------------------------------------
    // Incertidumbre
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Con dos pagos la banda es demasiado ancha para Plata")
    void usuarioNuevoConDosPagosNoLlegaAPlata() {
        List<OutcomeRecord> outcomes = List.of(
                event(10L, 50, PaymentOutcome.PUNTUAL, 1),
                event(11L, 50, PaymentOutcome.PUNTUAL, 3));

        ScoreResult result = calculate(outcomes);

        assertTrue(result.score() >= 60,
                "el puntaje alcanzaria para Plata: " + result.score());
        assertTrue(result.bandWidth() > 18,
                "pero la banda mide " + result.bandWidth() + " y Plata exige 18 o menos");
        assertNotEquals(ReputationLevel.SILVER, result.level());
    }

    @Test
    @DisplayName("La banda se cierra a medida que se acumula historial")
    void bandaSeCierraConMasHistorial() {
        ScoreResult pocos = calculate(punctualHistory(3, 3));
        ScoreResult muchos = calculate(punctualHistory(40, 5));

        assertTrue(muchos.bandWidth() < pocos.bandWidth(),
                "con 40 eventos la banda (" + muchos.bandWidth()
                        + ") deberia ser mas angosta que con 3 (" + pocos.bandWidth() + ")");
    }

    @Test
    @DisplayName("Sin historial, el score parte del promedio del grupo y no de cero")
    void sinEventosDevuelvePriorDelGrupo() {
        ScoreResult result = calculate(List.of());

        assertEquals(70.0, result.score(), 0.001,
                "empezar en cero afirmaria que la persona incumple siempre");
        assertEquals(ReputationLevel.NEW, result.level());
        assertEquals(0.0, result.effectiveCounterparties(), 0.001);
        assertTrue(result.breakdown().isEmpty());
    }

    // ------------------------------------------------------------------
    // Temporalidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un incumplimiento antiguo pesa menos que uno reciente")
    void atrasoAntiguoPesaMenos() {
        List<OutcomeRecord> reciente = List.of(
                event(10L, 50, PaymentOutcome.PUNTUAL, 2),
                event(11L, 50, PaymentOutcome.PUNTUAL, 4),
                event(12L, 50, PaymentOutcome.VENCIDO, 1));

        List<OutcomeRecord> antiguo = List.of(
                event(10L, 50, PaymentOutcome.PUNTUAL, 2),
                event(11L, 50, PaymentOutcome.PUNTUAL, 4),
                event(12L, 50, PaymentOutcome.VENCIDO, 360));

        double conRecientes = calculate(reciente).score();
        double conAntiguo = calculate(antiguo).score();

        assertTrue(conAntiguo > conRecientes,
                "el mismo incumplimiento a 360 dias (" + conAntiguo
                        + ") deberia pesar menos que a 1 dia (" + conRecientes + ")");
    }

    // ------------------------------------------------------------------
    // Equidad y estabilidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La misma conducta produce el mismo score con montos chicos y grandes")
    void equidadEntreGruposDeDistintoMonto() {
        GroupStats chico = new GroupStats(BigDecimal.valueOf(20), 0.70, 500);
        GroupStats grande = new GroupStats(BigDecimal.valueOf(500), 0.70, 500);

        List<OutcomeRecord> montosChicos = List.of(
                event(10L, 20, PaymentOutcome.PUNTUAL, 2),
                event(11L, 20, PaymentOutcome.PUNTUAL, 6),
                event(12L, 20, PaymentOutcome.VENCIDO, 10));

        List<OutcomeRecord> montosGrandes = List.of(
                event(10L, 500, PaymentOutcome.PUNTUAL, 2),
                event(11L, 500, PaymentOutcome.PUNTUAL, 6),
                event(12L, 500, PaymentOutcome.VENCIDO, 10));

        double scoreChico = calculator
                .calculate(montosChicos, Map.of(GROUP, chico), chico, Map.of(), NOW).score();
        double scoreGrande = calculator
                .calculate(montosGrandes, Map.of(GROUP, grande), grande, Map.of(), NOW).score();

        assertEquals(scoreChico, scoreGrande, 0.01,
                "castigar los montos pequenos seria lo contrario de la inclusion financiera");
    }

    @Test
    @DisplayName("Con historial largo, un evento aislado no desestabiliza el score")
    void unSoloEventoNoDesestabiliza() {
        List<OutcomeRecord> historial = punctualHistory(50, 5);
        List<OutcomeRecord> conUnFallo = new ArrayList<>(historial);
        conUnFallo.add(event(6L, 50, PaymentOutcome.VENCIDO, 0));

        double antes = calculate(historial).score();
        double despues = calculate(conUnFallo).score();

        assertTrue(Math.abs(antes - despues) < 5,
                "un evento movio el score de " + antes + " a " + despues);
    }

    @Test
    @DisplayName("El calculo es idempotente: mismos datos, mismo resultado")
    void recalculoEsIdempotente() {
        List<OutcomeRecord> outcomes = punctualHistory(12, 4);

        ScoreResult primera = calculate(outcomes);
        ScoreResult segunda = calculate(outcomes);

        assertEquals(primera.score(), segunda.score(), 1e-12);
        assertEquals(primera.bandLow(), segunda.bandLow(), 1e-12);
        assertEquals(primera.bandHigh(), segunda.bandHigh(), 1e-12);
        assertEquals(primera.level(), segunda.level());
    }

    // ------------------------------------------------------------------
    // Robustez numerica
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Los cuantiles Beta no explotan en los extremos")
    void betaQuantilesNoExplotanEnBordes() {
        double[][] extremos = {
                {0.0, 0.0}, {1e-9, 1e-9}, {1e9, 1.0}, {1.0, 1e9}, {1e9, 1e9}, {Double.NaN, 1.0}
        };

        for (double[] caso : extremos) {
            double[] band = BetaQuantiles.band(caso[0], caso[1]);
            assertTrue(Double.isFinite(band[0]) && Double.isFinite(band[1]),
                    "banda no finita para alpha=" + caso[0] + " beta=" + caso[1]);
            assertTrue(band[0] >= 0 && band[1] <= 100 && band[0] <= band[1],
                    "banda fuera de rango: [" + band[0] + ", " + band[1] + "]");
        }
    }

    @Test
    @DisplayName("Los parametros invalidos se rechazan al construirlos")
    void parametrosInvalidosSeRechazan() {
        assertThrowsIllegalArgument(() -> new ScoreParameters(
                -1, 0.35, 90, 3, 0.8, 0.5, 25, 60, 85, 18, 12, 2, 3, 4));
        assertThrowsIllegalArgument(() -> new ScoreParameters(
                4, 1.5, 90, 3, 0.8, 0.5, 25, 60, 85, 18, 12, 2, 3, 4));
        assertThrowsIllegalArgument(() -> new ScoreParameters(
                4, 0.35, 0, 3, 0.8, 0.5, 25, 60, 85, 18, 12, 2, 3, 4));
    }

    // ------------------------------------------------------------------
    // Objetivo del siguiente nivel y evidencia por evento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El objetivo dice cual de las tres condiciones falta, no solo el puntaje")
    void objetivoDistingueQueCondicionFalta() {
        // Cuarenta pagos puntuales con una sola contraparte: el puntaje sobra y
        // la banda esta cerrada, pero la diversidad no llega ni a Bronce.
        List<OutcomeRecord> outcomes = punctualHistory(40, 1);

        ScoreResult result = calculate(outcomes);
        NextLevelGoal goal = calculator.nextLevelGoal(result);

        assertEquals(ReputationLevel.BRONZE, goal.level());
        assertEquals(0.0, goal.missingScore(),
                "el puntaje ya alcanza: " + result.score());
        assertTrue(goal.missingCounterparties() > 0.9,
                "le falta cerca de una contraparte entera, no " + goal.missingCounterparties());
    }

    @Test
    @DisplayName("Quien ya esta en Oro no tiene siguiente objetivo")
    void enOroNoHayObjetivo() {
        ScoreResult result = calculate(punctualHistory(60, 6));

        assertEquals(ReputationLevel.GOLD, result.level(),
                "sesenta pagos puntuales con seis contrapartes deberian dar Oro");
        assertNull(calculator.nextLevelGoal(result),
                "no hay nivel por encima de Oro que describir");
    }

    @Test
    @DisplayName("La evidencia de un evento es la que el propio calculo agrupa")
    void evidenciaDeUnEventoCoincideConLaDelCalculo() {
        // Regresion de la evidencia inversa. Quien invoca al calculador tiene que
        // poder medir la direccion contraria con la misma vara; si esta cuenta
        // difiriera de la interna, el indice de reciprocidad compararia escalas
        // distintas y la defensa contra pares reciprocos dejaria de funcionar.
        List<OutcomeRecord> outcomes = List.of(
                event(10L, 50, PaymentOutcome.PUNTUAL, 1),
                event(10L, 150, PaymentOutcome.TARDIO, 40));

        double reported = 0.0;
        for (OutcomeRecord outcome : outcomes) {
            reported += calculator.evidenceMass(outcome, GROUPS, GLOBAL, NOW);
        }

        assertEquals(evidenceFor(outcomes, 10L), reported, 1e-9);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private ScoreResult calculate(List<OutcomeRecord> outcomes) {
        return calculator.calculate(outcomes, GROUPS, GLOBAL, Map.of(), NOW);
    }

    private OutcomeRecord event(long counterparty, double amount, PaymentOutcome outcome, long daysAgo) {
        return new OutcomeRecord(counterparty, GROUP, BigDecimal.valueOf(amount), outcome,
                NOW.minusDays(daysAgo));
    }

    /** Historial de pagos puntuales repartido entre varias contrapartes. */
    private List<OutcomeRecord> punctualHistory(int count, int counterparties) {
        List<OutcomeRecord> outcomes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            outcomes.add(event(10L + (i % counterparties), 50, PaymentOutcome.PUNTUAL, i));
        }
        return outcomes;
    }

    /** Evidencia acumulada frente a una contraparte, replicando peso por vigencia. */
    private double evidenceFor(List<OutcomeRecord> outcomes, long counterparty) {
        double total = 0.0;
        for (OutcomeRecord outcome : outcomes) {
            if (!outcome.counterpartyId().equals(counterparty)) {
                continue;
            }
            double ratio = outcome.amount().doubleValue() / GLOBAL.medianAmount().doubleValue();
            double weight = Math.min(1.0 + Math.log1p(ratio), 3.0);
            double days = java.time.Duration.between(outcome.resolvedAt(), NOW).toSeconds() / 86400.0;
            total += weight * Math.pow(2.0, -days / 90.0);
        }
        return total;
    }

    private void assertThrowsIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("se esperaba IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // correcto
        }
    }
}
