package com.pocketpeers.backend.pbl.application.internal.commandservices;

import com.pocketpeers.backend.pbl.application.internal.queryservices.OutcomeRecordAssembler;
import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.GroupStatsSnapshot;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.NextLevelGoal;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import com.pocketpeers.backend.pbl.domain.services.GroupStatsProvider;
import com.pocketpeers.backend.pbl.domain.services.PeerScoreCalculator;
import com.pocketpeers.backend.pbl.domain.services.PeerScoreService;
import com.pocketpeers.backend.pbl.infrastructure.configuration.PeerScoreProperties;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.ReputationEventRepository;
import com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories.UserReputationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PeerScoreServiceImpl implements PeerScoreService {

    // El calculador se instancia a mano y no se inyecta. No es un descuido: el
    // spec le prohibe depender de Spring para que los experimentos puedan
    // correrlo cientos de miles de veces sin levantar un contexto, y hay una
    // prueba que falla si aparece una anotacion de framework en la clase.
    private final PeerScoreCalculator calculator = new PeerScoreCalculator();

    private final UserRepository userRepository;
    private final UserReputationRepository userReputationRepository;
    private final ReputationEventRepository reputationEventRepository;
    private final GroupStatsProvider groupStatsProvider;
    private final PeerScoreProperties properties;

    public PeerScoreServiceImpl(UserRepository userRepository,
                                UserReputationRepository userReputationRepository,
                                ReputationEventRepository reputationEventRepository,
                                GroupStatsProvider groupStatsProvider,
                                PeerScoreProperties properties) {
        this.userRepository = userRepository;
        this.userReputationRepository = userReputationRepository;
        this.reputationEventRepository = reputationEventRepository;
        this.groupStatsProvider = groupStatsProvider;
        this.properties = properties;
    }

    /**
     * Ultimo score calculado por usuario, con el instante en que se calculo.
     *
     * <p>Solo sirve a {@link #recalculate(Long)}, que es la ruta de lectura: el
     * panel la invoca en cada carga y recalcular desde el historial completo
     * cada vez se nota con cientos de usuarios. Las rutas de escritura no la
     * consultan nunca, y ademas la invalidan.</p>
     */
    private final Map<Long, CachedScore> scoreCache = new ConcurrentHashMap<>();

    /** Tope de seguridad para que la cache no crezca sin limite. */
    private static final int MAX_CACHED_SCORES = 10_000;

    private record CachedScore(ScoreResult result, Instant computedAt) {
    }

    @Override
    @Transactional
    public ScoreResult recalculate(Long userId) {
        var cached = cachedScoreFor(userId);
        if (cached != null) {
            return cached;
        }
        var recalculation = recalculate(userId, groupStatsProvider.snapshot(), LocalDateTime.now());
        if (recalculation == null) {
            throw new RuntimeException("User not found");
        }
        rememberScore(userId, recalculation.result());
        return recalculation.result();
    }

    private ScoreResult cachedScoreFor(Long userId) {
        long ttl = properties.getScoreCacheSeconds();
        if (ttl <= 0) {
            return null;
        }
        var entry = scoreCache.get(userId);
        if (entry == null) {
            return null;
        }
        if (Duration.between(entry.computedAt(), Instant.now()).getSeconds() >= ttl) {
            scoreCache.remove(userId);
            return null;
        }
        return entry.result();
    }

    private void rememberScore(Long userId, ScoreResult result) {
        if (properties.getScoreCacheSeconds() <= 0) {
            return;
        }
        // Vaciarla entera al pasarse del tope es mas barato que llevar orden de
        // antiguedad, y el efecto es un recalculo extra por usuario activo.
        if (scoreCache.size() >= MAX_CACHED_SCORES) {
            scoreCache.clear();
        }
        scoreCache.put(userId, new CachedScore(result, Instant.now()));
    }

    /**
     * Invalida lo cacheado tras un cambio real.
     *
     * <p>Es lo que hace aceptable la cache: un pago se refleja de inmediato en
     * lugar de esperar a que expire la ventana.</p>
     */
    private void forgetScores(Long... userIds) {
        for (Long userId : userIds) {
            if (userId != null) {
                scoreCache.remove(userId);
            }
        }
    }

    @Override
    @Transactional
    public void recalculateAfterEvent(Long userId, Long counterpartyId) {
        forgetScores(userId, counterpartyId);
        // Una sola foto para los dos: si se tomaran por separado y las
        // estadisticas cambiaran en medio, cada uno quedaria medido con una regla
        // distinta justo en el momento en que se los compara entre si.
        var stats = groupStatsProvider.snapshot();
        var now = LocalDateTime.now();

        recalculate(userId, stats, now);
        if (counterpartyId != null && !counterpartyId.equals(userId)) {
            // Si la contraparte ya no existe se ignora en silencio: esto corre
            // dentro de la confirmacion de un pago, y no vale hacer fallar un
            // pago real porque no se pudo actualizar la reputacion de un tercero.
            recalculate(counterpartyId, stats, now);
        }
    }

    @Override
    @Transactional
    public List<Long> recalculateAll() {
        scoreCache.clear();
        var stats = groupStatsProvider.snapshot();
        var now = LocalDateTime.now();

        List<Long> levelChanges = new ArrayList<>();
        for (UserReputation reputation : userReputationRepository.findAll()) {
            var userId = reputation.getUser().getId();
            var recalculation = recalculate(userId, stats, now);
            if (recalculation != null && recalculation.levelChanged()) {
                levelChanges.add(userId);
            }
        }
        return levelChanges;
    }

    @Override
    @Transactional
    public Map<Long, ScoreResult> recalculateFor(List<Long> userIds) {
        forgetScores(userIds.toArray(new Long[0]));
        // Una sola foto y un solo instante para todo el conjunto, por el mismo
        // motivo que recalculateAfterEvent: estos resultados se van a comparar
        // entre si, y medir a cada uno con estadisticas o con un reloj distinto
        // los volveria incomparables justo donde se los pone en una tabla.
        var stats = groupStatsProvider.snapshot();
        var now = LocalDateTime.now();

        Map<Long, ScoreResult> results = new LinkedHashMap<>();
        for (Long userId : userIds) {
            if (userId == null || results.containsKey(userId)) {
                continue;
            }
            var recalculation = recalculate(userId, stats, now);
            // Un usuario que ya no existe simplemente no aparece en el mapa:
            // quien llama decide que hacer con el, y aqui no hay nada que
            // calcular sobre alguien que no esta.
            if (recalculation != null) {
                results.put(userId, recalculation.result());
            }
        }
        return results;
    }

    @Override
    public NextLevelGoal goalFor(ScoreResult result) {
        return calculator.nextLevelGoal(result);
    }

    /**
     * El recalculo propiamente dicho, con las estadisticas y el instante dados.
     *
     * @return null si el usuario no existe
     */
    private Recalculation recalculate(Long userId, GroupStatsSnapshot stats, LocalDateTime now) {
        var user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        var reputation = userReputationRepository.findByUser_Id(userId)
                .orElseGet(() -> new UserReputation(user));
        var previousLevel = reputation.getPeerLevel();

        var outcomes = OutcomeRecordAssembler.toOutcomes(
                reputationEventRepository.findAllByUser_IdAndCounterpartyIdIsNotNullAndAmountIsNotNull(userId));

        var result = calculator.calculate(outcomes, stats.byGroup(), stats.global(),
                reverseEvidence(userId, stats, now), now);

        reputation.applyPeerScore(result, properties.getAlgoVersion(), now);
        userReputationRepository.save(reputation);

        return new Recalculation(result, previousLevel != result.level());
    }

    /**
     * Evidencia que cada contraparte devuelve hacia este usuario.
     *
     * <p>Son las obligaciones en las que el usuario fue el acreedor, agrupadas
     * por quien le debia. Comparar esa masa con la del sentido contrario es lo
     * que detecta a dos cuentas que se pagan mutuamente para inflarse: en una
     * relacion normal los dos lados no se equilibran casi nunca.</p>
     *
     * <p>La masa se pide al calculador en vez de calcularla aqui. La
     * reciprocidad compara las dos direcciones, y solo tiene sentido si ambas se
     * midieron con la misma formula de peso y decaimiento.</p>
     */
    private Map<Long, Double> reverseEvidence(Long userId, GroupStatsSnapshot stats, LocalDateTime now) {
        Map<Long, Double> reverse = new HashMap<>();
        for (var event : reputationEventRepository.findAllByCounterpartyIdAndAmountIsNotNull(userId)) {
            var outcome = OutcomeRecordAssembler.toOutcome(event);
            if (outcome == null || event.getUser() == null) {
                continue;
            }
            reverse.merge(event.getUser().getId(),
                    calculator.evidenceMass(outcome, stats.byGroup(), stats.global(), now),
                    Double::sum);
        }
        return reverse;
    }

    private record Recalculation(ScoreResult result, boolean levelChanged) {
    }
}
