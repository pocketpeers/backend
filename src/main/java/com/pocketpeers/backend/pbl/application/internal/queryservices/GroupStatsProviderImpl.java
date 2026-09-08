package com.pocketpeers.backend.pbl.application.internal.queryservices;

import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStats;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStatsSnapshot;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.OutcomeRecord;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreParameters;
import com.pocketpeers.backend.pbl.domain.services.GroupStatsProvider;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula la mediana de montos y la tasa de cumplimiento de cada grupo.
 *
 * <p>La mediana es lo que vuelve comparables grupos de escalas distintas: el
 * peso de un evento se mide contra ella y no contra un valor en soles, asi que
 * cumplir con S/20 en un grupo que mueve S/20 vale lo mismo que cumplir con
 * S/500 en uno que mueve S/500. Es la propiedad de equidad del algoritmo, y
 * depende por completo de que estas estadisticas existan.</p>
 */
@Service
public class GroupStatsProviderImpl implements GroupStatsProvider {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final ReputationEventRepository reputationEventRepository;
    private final PeerScoreProperties properties;

    // La foto se comparte entre hilos: un recalculo nocturno y una peticion HTTP
    // pueden pedirla a la vez. Se publica de una sola vez y ya construida, asi
    // que un lector ve la anterior completa o la nueva completa, nunca una a
    // medias. Dos hilos pueden calcularla en paralelo al expirar, y eso es
    // preferible a serializarlos: el resultado es el mismo y la operacion es de
    // solo lectura.
    private volatile CachedSnapshot cached;

    public GroupStatsProviderImpl(ReputationEventRepository reputationEventRepository,
                                  PeerScoreProperties properties) {
        this.reputationEventRepository = reputationEventRepository;
        this.properties = properties;
    }

    @Override
    public GroupStatsSnapshot snapshot() {
        CachedSnapshot current = cached;
        if (current != null && !current.isExpired(properties.getStatsCacheSeconds())) {
            return current.snapshot();
        }
        GroupStatsSnapshot fresh = compute();
        if (fresh.global().eventCount() > 0) {
            cached = new CachedSnapshot(fresh, Instant.now());
        }
        return fresh;
    }

    @Override
    public void invalidate() {
        cached = null;
    }

    private GroupStatsSnapshot compute() {
        var outcomes = OutcomeRecordAssembler.toOutcomes(
                reputationEventRepository.findAllByCounterpartyIdIsNotNullAndAmountIsNotNull());

        Map<Long, List<OutcomeRecord>> byGroup = new HashMap<>();
        for (OutcomeRecord outcome : outcomes) {
            // Los eventos sin grupo no pertenecen a ninguna escala local, pero si
            // cuentan para la global: son conducta observada igual que el resto.
            if (outcome.groupId() != null) {
                byGroup.computeIfAbsent(outcome.groupId(), key -> new ArrayList<>()).add(outcome);
            }
        }

        Map<Long, GroupStats> stats = new HashMap<>();
        byGroup.forEach((groupId, groupOutcomes) -> stats.put(groupId, statsOf(groupOutcomes)));

        return new GroupStatsSnapshot(stats, globalStats(outcomes));
    }

    /**
     * Estadisticas globales, que hacen de respaldo de todo grupo pequeno.
     *
     * <p>La tasa observada solo se usa cuando hay suficientes eventos para que
     * signifique algo. Debajo de ese piso se usa la neutra, aunque la mediana
     * si sea la observada: son dos cosas distintas, y una mediana calculada con
     * pocos montos ya sirve de escala mientras que una tasa calculada con pocos
     * desenlaces no sirve de prior.</p>
     *
     * <p>El piso no es opcional. El prior entra como {@code alpha = k * tasa} y
     * {@code beta = k * (1 - tasa)}: con los primeros pagos del sistema todos
     * puntuales la tasa es 1.0, y entonces {@code beta} vale 0. Eso hace dos
     * cosas a la vez, las dos falsas. Cualquier usuario sin un solo pago recibe
     * score 100, porque su posterior es solo el prior. Y la banda de quien tiene
     * tres pagos se cierra casi por completo, cuando su unica funcion es decir
     * que el sistema todavia no sabe lo suficiente.</p>
     *
     * <p>El umbral es el mismo {@code MIN_GROUP_EVENTS} que {@code isReliable()}
     * aplica a los grupos. El spec define esa regla de respaldo pero
     * {@code GroupStats.resolve} solo la aplica a las estadisticas por grupo, y
     * nunca a las globales: si las globales tampoco son confiables no hay
     * ningun respaldo mas al que caer, y ahi es donde se usa la tasa neutra.</p>
     */
    private GroupStats globalStats(List<OutcomeRecord> outcomes) {
        if (!outcomes.isEmpty() && outcomes.size() < ScoreParameters.MIN_GROUP_EVENTS) {
            return new GroupStats(median(outcomes), properties.getNeutralOnTimeRate(), outcomes.size());
        }
        if (outcomes.isEmpty()) {
            // Sistema sin historial. La mediana es un relleno: no hay nada que
            // pesar todavia, pero tiene que ser positiva porque es un divisor.
            //
            // Por eso esta foto NO se cachea, y no es un detalle. Con una
            // mediana de 1, el primer pago real de S/50 pesaria
            // 1 + ln(1 + 50/1) = 4.93, recortado al techo de 3.0: todos los
            // eventos del sistema saturarian su peso hasta que la cache
            // expirara, y los primeros usuarios saldrian con scores altisimos a
            // partir de tres pagos. La escala tiene que aparecer en cuanto exista
            // el primer evento que la defina.
            return new GroupStats(BigDecimal.ONE, properties.getNeutralOnTimeRate(), 0);
        }
        return statsOf(outcomes);
    }

    private GroupStats statsOf(List<OutcomeRecord> outcomes) {
        return new GroupStats(median(outcomes), onTimeRate(outcomes), outcomes.size());
    }

    /**
     * Mediana y no promedio: un solo gasto atipico corre el promedio y con el la
     * escala de todo el grupo, mientras que la mediana lo ignora.
     */
    private BigDecimal median(List<OutcomeRecord> outcomes) {
        List<BigDecimal> amounts = outcomes.stream()
                .map(OutcomeRecord::amount)
                .sorted(Comparator.naturalOrder())
                .toList();
        int size = amounts.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return amounts.get(middle);
        }
        return amounts.get(middle - 1).add(amounts.get(middle)).divide(TWO, 4, RoundingMode.HALF_UP);
    }

    /**
     * Tasa de cumplimiento como promedio del valor de los desenlaces.
     *
     * <p>El spec pide "tasa de cumplimiento" sin fijar como se mide. Se toma el
     * promedio de {@code v} y no la proporcion de eventos puntuales porque es la
     * misma escala en la que el posterior acumula evidencia, de modo que el prior
     * y los datos hablan el mismo idioma. Ademas degrada con sentido: un grupo de
     * puros abonos parciales a tiempo da 0.6, no 0 ni 1.</p>
     */
    private double onTimeRate(List<OutcomeRecord> outcomes) {
        double total = 0.0;
        for (OutcomeRecord outcome : outcomes) {
            total += outcome.outcome().value();
        }
        return total / outcomes.size();
    }

    private record CachedSnapshot(GroupStatsSnapshot snapshot, Instant takenAt) {
        boolean isExpired(long ttlSeconds) {
            return Duration.between(takenAt, Instant.now()).getSeconds() >= ttlSeconds;
        }
    }
}
