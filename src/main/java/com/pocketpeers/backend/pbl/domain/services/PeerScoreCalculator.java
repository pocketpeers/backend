package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStats;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.OutcomeRecord;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreParameters;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula la reputacion de un usuario a partir del desenlace de sus obligaciones.
 *
 * <p>A diferencia del contador PBL que reemplaza, el score no se acumula: se
 * recalcula completo cada vez a partir del historial. Eso significa que el
 * resultado depende solo de los hechos registrados, no del orden en que se
 * procesaron ni de cuantas veces se corrio el calculo.</p>
 *
 * <h2>Por que esta clase no depende de Spring ni de la base de datos</h2>
 *
 * <p>Es una funcion pura a proposito. Recibe los eventos ya cargados y el
 * instante de calculo como argumento, y no consulta nada. Eso permite dos cosas
 * que de otro modo serian inviables: correr el algoritmo cientos de miles de
 * veces sobre cohortes sinteticas para validarlo, y avanzar el reloj
 * artificialmente para medir como envejece un historial.</p>
 *
 * <p>Si alguna vez hace falta un dato que no esta aqui, la solucion es recibirlo
 * como parametro, no inyectar un repositorio.</p>
 */
public final class PeerScoreCalculator {

    private static final int MAX_CAP_ITERATIONS = 10;
    private static final double EPSILON = 1e-9;
    private static final double LEVEL_TOLERANCE = 1e-6;
    private static final long RECENT_DAYS = 30;

    /** Umbral a partir del cual un desenlace se considera cumplimiento. */
    private static final double FULFILLED_THRESHOLD = 0.6;

    private final ScoreParameters params;

    public PeerScoreCalculator(ScoreParameters params) {
        this.params = params;
    }

    public PeerScoreCalculator() {
        this(ScoreParameters.defaults());
    }

    /**
     * Calcula el score de un usuario.
     *
     * @param outcomes         desenlaces de las obligaciones del usuario
     * @param statsByGroup     estadisticas por grupo, para escalar montos y fijar el prior
     * @param globalStats      respaldo cuando un grupo tiene pocos eventos
     * @param reverseEvidence  evidencia en sentido inverso por contraparte, para medir
     *                         reciprocidad. El calculador no puede obtenerla por su cuenta
     *                         porque solo ve los eventos de este usuario; la aporta quien
     *                         llama. Un mapa vacio significa "sin reciprocidad conocida"
     * @param now              instante de calculo, explicito para poder simular el paso del tiempo
     */
    public ScoreResult calculate(List<OutcomeRecord> outcomes,
                                 Map<Long, GroupStats> statsByGroup,
                                 GroupStats globalStats,
                                 Map<Long, Double> reverseEvidence,
                                 LocalDateTime now) {

        Map<Long, GroupStats> groupStats = statsByGroup == null ? Map.of() : statsByGroup;
        Map<Long, Double> reverse = reverseEvidence == null ? Map.of() : reverseEvidence;

        if (outcomes == null || outcomes.isEmpty()) {
            return priorOnlyResult(globalStats);
        }

        List<Weighted> weighted = weigh(outcomes, groupStats, globalStats, now);
        double prior = weightedPrior(weighted, groupStats, globalStats);

        Posterior posterior = posteriorFrom(weighted, reverse, prior);
        if (posterior == null) {
            // Todo el historial decayo hasta ser irrelevante: equivale a no tener historial.
            return priorOnlyResult(globalStats);
        }

        double score = 100.0 * posterior.alpha / (posterior.alpha + posterior.beta);
        double[] band = BetaQuantiles.band(posterior.alpha, posterior.beta);
        double effective = effectiveCounterparties(posterior.cappedEvidence);
        int distinct = posterior.cappedEvidence.size();
        ReputationLevel level = resolveLevel(score, band[1] - band[0], effective);
        List<ScoreResult.Contribution> breakdown =
                buildBreakdown(weighted, reverse, prior, score, now);

        return new ScoreResult(score, band[0], band[1], level, effective, distinct,
                posterior.alpha, posterior.beta, breakdown);
    }

    // ------------------------------------------------------------------
    // Paso 1: peso por exposicion y vigencia
    // ------------------------------------------------------------------

    /**
     * Asigna a cada evento su peso por monto y su factor de vigencia.
     *
     * <p>El peso crece con el monto de forma logaritmica: cumplir con S/500 es
     * evidencia mas fuerte que cumplir con S/5, pero no cien veces mas fuerte. El
     * tope evita que un unico gasto atipico domine todo el historial.</p>
     *
     * <p>La vigencia decae con vida media configurable. Sin decaimiento no existe
     * rehabilitacion: un atraso de hace dos anos pesaria igual que uno de ayer y
     * nadie tendria incentivo para mejorar.</p>
     */
    private List<Weighted> weigh(List<OutcomeRecord> outcomes,
                                 Map<Long, GroupStats> groupStats,
                                 GroupStats globalStats,
                                 LocalDateTime now) {
        List<Weighted> result = new ArrayList<>(outcomes.size());
        for (OutcomeRecord outcome : outcomes) {
            GroupStats stats = GroupStats.resolve(outcome.groupId(), groupStats, globalStats);
            double ratio = outcome.amount().doubleValue() / stats.medianAmount().doubleValue();
            double weight = Math.min(1.0 + Math.log1p(ratio), params.maxEventWeight());

            // El decaimiento se mide en segundos y no en dias enteros: si se
            // redondeara a dias, todos los eventos de una misma jornada pesarian
            // igual y el score daria saltos a medianoche.
            double elapsedDays = Duration.between(outcome.resolvedAt(), now).toSeconds() / 86400.0;
            double decay = Math.pow(2.0, -Math.max(0.0, elapsedDays) / params.halfLifeDays());

            result.add(new Weighted(outcome, weight, decay));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Pasos 2 a 5: topes, reciprocidad y posterior
    // ------------------------------------------------------------------

    private Posterior posteriorFrom(List<Weighted> weighted, Map<Long, Double> reverse, double prior) {
        Map<Long, Double> evidence = new LinkedHashMap<>();
        for (Weighted w : weighted) {
            evidence.merge(w.record.counterpartyId(), w.mass(), Double::sum);
        }
        if (evidence.isEmpty() || sum(evidence) < EPSILON) {
            return null;
        }

        Map<Long, Double> capped = applyCounterpartyCap(evidence);
        Map<Long, Double> rho = discountFactors(evidence, capped, reverse);

        double alpha = params.priorStrength() * prior;
        double beta = params.priorStrength() * (1.0 - prior);

        for (Weighted w : weighted) {
            double contribution = rho.getOrDefault(w.record.counterpartyId(), 1.0) * w.mass();
            double value = w.record.outcome().value();
            alpha += contribution * value;
            beta += contribution * (1.0 - value);
        }
        return new Posterior(alpha, beta, capped);
    }

    /**
     * Limita cuanta evidencia puede aportar una sola contraparte.
     *
     * <p>Es la defensa contra colusion, y no funciona detectando la trampa sino
     * haciendola inutil: por muchos pagos que dos cuentas se inventen entre si,
     * ninguna puede representar mas que la fraccion configurada del total. Subir
     * de nivel exige contrapartes distintas, o sea reclutar personas reales.</p>
     *
     * <p>El piso {@code 1/n} sobre el tope no es un detalle menor. Con tope 0.35 y
     * solo dos contrapartes la restriccion es imposible de satisfacer, porque
     * 2 x 35% no llega a cubrir el total; sin el piso, cada iteracion recorta un
     * poco mas y la evidencia colapsa a cero. El piso vuelve el problema factible
     * y el bucle converge.</p>
     */
    private Map<Long, Double> applyCounterpartyCap(Map<Long, Double> evidence) {
        double effectiveCap = Math.max(params.counterpartyCap(), 1.0 / evidence.size());
        Map<Long, Double> capped = new LinkedHashMap<>(evidence);

        for (int iteration = 0; iteration < MAX_CAP_ITERATIONS; iteration++) {
            double total = sum(capped);
            double limit = effectiveCap * total;
            boolean changed = false;
            for (Map.Entry<Long, Double> entry : capped.entrySet()) {
                if (entry.getValue() > limit + EPSILON) {
                    entry.setValue(limit);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return capped;
    }

    /**
     * Descuento aplicado a cada contraparte: el recorte por tope, mas la
     * penalizacion por reciprocidad.
     *
     * <p>Una reciprocidad cercana a 1 significa que los dos se deben y se pagan en
     * la misma medida, que es la firma de un anillo de colusion. La penalizacion
     * es continua y no un corte brusco, para que una pareja que legitimamente se
     * presta dinero de ida y vuelta no quede arruinada por cruzar un umbral.</p>
     */
    private Map<Long, Double> discountFactors(Map<Long, Double> evidence,
                                              Map<Long, Double> capped,
                                              Map<Long, Double> reverse) {
        Map<Long, Double> rho = new HashMap<>();
        for (Map.Entry<Long, Double> entry : evidence.entrySet()) {
            Long counterparty = entry.getKey();
            double raw = entry.getValue();
            double factor = raw < EPSILON ? 1.0 : capped.get(counterparty) / raw;

            double reverseMass = reverse.getOrDefault(counterparty, 0.0);
            double reciprocity = reciprocityIndex(raw, reverseMass);
            if (reciprocity > params.reciprocityThreshold()) {
                double excess = (reciprocity - params.reciprocityThreshold())
                        / (1.0 - params.reciprocityThreshold());
                factor *= 1.0 - params.reciprocityPenalty() * excess;
            }
            rho.put(counterparty, factor);
        }
        return rho;
    }

    private double reciprocityIndex(double forward, double backward) {
        double high = Math.max(forward, backward);
        if (high < EPSILON) {
            return 0.0;
        }
        return Math.min(forward, backward) / high;
    }

    // ------------------------------------------------------------------
    // Paso 6: diversidad
    // ------------------------------------------------------------------

    /**
     * Contrapartes efectivas, como inverso del indice de concentracion.
     *
     * <p>Diez contrapartes donde una concentra el 90% de la evidencia no valen lo
     * mismo que diez repartidas parejo. Este numero refleja esa diferencia:
     * responde "con cuantas personas distintas te has comportado de verdad".</p>
     */
    private double effectiveCounterparties(Map<Long, Double> capped) {
        double total = sum(capped);
        if (total < EPSILON) {
            return 0.0;
        }
        double concentration = 0.0;
        for (double value : capped.values()) {
            double share = value / total;
            concentration += share * share;
        }
        return concentration < EPSILON ? 0.0 : 1.0 / concentration;
    }

    // ------------------------------------------------------------------
    // Paso 7: nivel
    // ------------------------------------------------------------------

    /**
     * Resuelve el nivel exigiendo tres condiciones a la vez: puntaje, certeza y
     * diversidad.
     *
     * <p>Las tres son conjuntivas y esa es la diferencia de fondo con el motor
     * anterior. Un usuario con puntaje 90 pero solo dos contrapartes se queda en
     * Bronce, porque el sistema no tiene con que distinguirlo de dos cuentas que
     * se pagan entre si.</p>
     */
    private ReputationLevel resolveLevel(double score, double bandWidth, double effectiveCounterparties) {
        if (atLeast(score, params.goldScore())
                && atMost(bandWidth, params.goldMaxBandWidth())
                && atLeast(effectiveCounterparties, params.goldMinCounterparties())) {
            return ReputationLevel.GOLD;
        }
        if (atLeast(score, params.silverScore())
                && atMost(bandWidth, params.silverMaxBandWidth())
                && atLeast(effectiveCounterparties, params.silverMinCounterparties())) {
            return ReputationLevel.SILVER;
        }
        if (atLeast(score, params.bronzeScore())
                && atLeast(effectiveCounterparties, params.bronzeMinCounterparties())) {
            return ReputationLevel.BRONZE;
        }
        return ReputationLevel.NEW;
    }

    /**
     * Comparaciones de umbral con tolerancia.
     *
     * <p>No es cosmetica. Un usuario con exactamente dos contrapartes
     * equilibradas tiene 2.0 contrapartes efectivas en teoria, pero el bucle de
     * topes converge de forma geometrica y deja un residuo del orden de 1e-11.
     * Sin tolerancia, esa persona se queda en Nuevo por un error de redondeo que
     * no tiene nada que ver con su conducta. Lo mismo aplica a quien queda justo
     * en el umbral de puntaje.</p>
     */
    private static boolean atLeast(double value, double threshold) {
        return value >= threshold - LEVEL_TOLERANCE;
    }

    private static boolean atMost(double value, double threshold) {
        return value <= threshold + LEVEL_TOLERANCE;
    }

    // ------------------------------------------------------------------
    // Paso 8: desglose explicativo
    // ------------------------------------------------------------------

    /**
     * Explica de donde sale el score, por exclusion.
     *
     * <p>Para cada bloque de eventos se recalcula el score como si ese bloque no
     * existiera; la diferencia es su aporte. Es atribucion marginal y no una suma
     * de puntos, asi que los deltas no tienen por que sumar el score total. Se
     * prefirio este metodo porque responde la pregunta que la persona realmente se
     * hace: "cuanto me esta costando ese atraso".</p>
     *
     * <p>Los bloques se arman por antiguedad y por desenlace. No se separan por
     * grupo porque el calculador solo conoce identificadores, no nombres; si se
     * quiere un desglose por grupo, el servicio puede enriquecerlo despues.</p>
     */
    private List<ScoreResult.Contribution> buildBreakdown(List<Weighted> weighted,
                                                          Map<Long, Double> reverse,
                                                          double prior,
                                                          double score,
                                                          LocalDateTime now) {
        LocalDateTime recentBoundary = now.minusDays(RECENT_DAYS);
        Map<String, List<Weighted>> buckets = new LinkedHashMap<>();
        for (Weighted w : weighted) {
            boolean recent = w.record.resolvedAt().isAfter(recentBoundary);
            boolean fulfilled = w.record.outcome().value() >= FULFILLED_THRESHOLD;
            String label = label(recent, fulfilled);
            buckets.computeIfAbsent(label, key -> new ArrayList<>()).add(w);
        }

        List<ScoreResult.Contribution> contributions = new ArrayList<>();
        for (Map.Entry<String, List<Weighted>> bucket : buckets.entrySet()) {
            List<Weighted> remaining = new ArrayList<>(weighted);
            remaining.removeAll(bucket.getValue());

            double without;
            if (remaining.isEmpty()) {
                without = 100.0 * prior;
            } else {
                Posterior posterior = posteriorFrom(remaining, reverse, prior);
                without = posterior == null
                        ? 100.0 * prior
                        : 100.0 * posterior.alpha / (posterior.alpha + posterior.beta);
            }
            contributions.add(new ScoreResult.Contribution(bucket.getKey(), score - without));
        }
        return Collections.unmodifiableList(contributions);
    }

    private String label(boolean recent, boolean fulfilled) {
        if (recent) {
            return fulfilled ? "Cumplimiento reciente" : "Incumplimiento reciente";
        }
        return fulfilled ? "Cumplimiento anterior" : "Incumplimiento anterior";
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /**
     * Resultado para quien no tiene historial utilizable.
     *
     * <p>Arranca en el promedio de su grupo y no en cero. Empezar en cero seria
     * afirmar que la persona incumple siempre, que es justo lo contrario de lo que
     * se sabe: no se sabe nada. La banda ancha es la que comunica esa ignorancia.</p>
     */
    private ScoreResult priorOnlyResult(GroupStats globalStats) {
        double prior = globalStats.onTimeRate();
        double alpha = params.priorStrength() * prior;
        double beta = params.priorStrength() * (1.0 - prior);
        double[] band = BetaQuantiles.band(alpha, beta);
        return new ScoreResult(100.0 * prior, band[0], band[1], ReputationLevel.NEW,
                0.0, 0, alpha, beta, List.of());
    }

    /**
     * Prior del usuario: promedio de las tasas de sus grupos, ponderado por cuanta
     * evidencia aporta cada uno.
     *
     * <p>Un usuario que participa en varios grupos no deberia heredar el prior de
     * uno solo. Ponderar por evidencia hace que pese mas el grupo donde
     * efectivamente opera.</p>
     */
    private double weightedPrior(List<Weighted> weighted,
                                 Map<Long, GroupStats> groupStats,
                                 GroupStats globalStats) {
        double weightedSum = 0.0;
        double totalMass = 0.0;
        for (Weighted w : weighted) {
            GroupStats stats = GroupStats.resolve(w.record.groupId(), groupStats, globalStats);
            weightedSum += stats.onTimeRate() * w.mass();
            totalMass += w.mass();
        }
        return totalMass < EPSILON ? globalStats.onTimeRate() : weightedSum / totalMass;
    }

    private static double sum(Map<Long, Double> values) {
        double total = 0.0;
        for (double value : values.values()) {
            total += value;
        }
        return total;
    }

    /** Evento con su peso y vigencia ya resueltos. */
    private record Weighted(OutcomeRecord record, double weight, double decay) {
        /** Evidencia que aporta este evento: peso por vigencia. */
        double mass() {
            return weight * decay;
        }
    }

    /** Posterior Beta junto con la evidencia ya topada, que se reutiliza para la diversidad. */
    private record Posterior(double alpha, double beta, Map<Long, Double> cappedEvidence) {
    }
}
