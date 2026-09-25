package com.pocketpeers.backend.users.application.internal.identityverification;

import com.pocketpeers.backend.users.domain.exceptions.DniLookupUnavailableException;
import com.pocketpeers.backend.users.domain.exceptions.IdentityLookupLimitException;
import com.pocketpeers.backend.users.domain.exceptions.IdentityMismatchException;
import com.pocketpeers.backend.users.domain.model.entities.IdentityLookupAttempt;
import com.pocketpeers.backend.users.domain.model.valueobjects.DocumentType;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityDocument;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityLookupOutcome;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerification;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerificationStatus;
import com.pocketpeers.backend.users.domain.model.valueobjects.OfficialName;
import com.pocketpeers.backend.users.domain.model.valueobjects.SignUpOrigin;
import com.pocketpeers.backend.users.domain.ports.out.DniLookupPort;
import com.pocketpeers.backend.users.domain.services.NameMatcher;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.IdentityLookupAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifica, durante el registro, que el nombre escrito corresponda al DNI.
 *
 * <p>Todo gira alrededor de una restriccion: la fuente externa da <b>100
 * consultas al mes</b> y la cohorte del estudio es de hasta 100 personas. No
 * sobra ninguna, asi que cada consulta se protege con cuatro capas:</p>
 *
 * <ol>
 *   <li><b>Cache por DNI (24 h).</b> Quien se equivoca al escribir su nombre y
 *       lo corrige no gasta otra consulta: se compara contra la respuesta que
 *       ya se tenia. En la sesion presencial es el caso mas comun.</li>
 *   <li><b>Por dispositivo:</b> hasta 3 DNI distintos y 5 intentos fallidos en
 *       24 horas. Frena a quien prueba documentos o nombres al azar.</li>
 *   <li><b>Por IP:</b> hasta 20 consultas por hora. Respaldo para quien llame a
 *       la API sin la aplicacion e invente un dispositivo en cada intento.</li>
 *   <li><b>Tope global:</b> hasta 15 consultas en 24 horas, de cualquier
 *       origen. Protege la cuota del mes aunque todo lo demas falle.</li>
 * </ol>
 *
 * <p>Las dos primeras capas de limite bloquean el alta, porque las alcanza una
 * persona concreta que esta insistiendo. El tope global y las fallas de la
 * fuente <b>no bloquean</b>: dejan la cuenta como pendiente de verificar. Una
 * sesion grupal no puede quedarse sin registrar a nadie porque el servicio
 * externo se cayo o porque otros agotaron la cuota del dia.</p>
 */
@Service
public class IdentityVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityVerificationService.class);

    /** Balde comun para las peticiones que no traen dispositivo o IP. */
    private static final String UNKNOWN = "desconocido";

    private final DniLookupPort dniLookupPort;
    private final IdentityLookupAttemptRepository attemptRepository;
    private final IdentityLookupAttemptRecorder attemptRecorder;

    /**
     * Apagado por defecto: en desarrollo y en las pruebas no debe salir ninguna
     * consulta real. Se enciende solo en el servidor, junto con el token.
     */
    @Value("${identity-verification.enabled:false}")
    private boolean enabled;

    @Value("${identity-verification.max-dnis-per-device:3}")
    private int maxDnisPerDevice;

    @Value("${identity-verification.max-failed-per-device:5}")
    private int maxFailedPerDevice;

    @Value("${identity-verification.max-lookups-per-ip-hour:20}")
    private int maxLookupsPerIpHour;

    @Value("${identity-verification.max-lookups-per-day:15}")
    private int maxLookupsPerDay;

    @Value("${identity-verification.cache-hours:24}")
    private int cacheHours;

    /**
     * Secreto para los hash de dispositivo, IP y DNI. Si no se define uno
     * propio se reutiliza el del JWT, que ya es secreto en el servidor.
     */
    @Value("${identity-verification.hash-secret:${authorization.jwt.secret:pocketpeers}}")
    private String hashSecret;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * Respuestas recientes de la fuente, por hash de DNI.
     *
     * <p>Solo en memoria, a proposito: asi el nombre oficial nunca queda escrito
     * en la base. Si el servidor se reinicia se pierde, y lo peor que pasa es una
     * consulta de mas.</p>
     */
    private final Map<String, CachedName> cache = new ConcurrentHashMap<>();

    public IdentityVerificationService(DniLookupPort dniLookupPort,
                                       IdentityLookupAttemptRepository attemptRepository,
                                       IdentityLookupAttemptRecorder attemptRecorder) {
        this.dniLookupPort = dniLookupPort;
        this.attemptRepository = attemptRepository;
        this.attemptRecorder = attemptRecorder;
    }

    /**
     * @return el resultado a guardar con la cuenta
     * @throws IdentityMismatchException    si el nombre no corresponde al DNI
     * @throws IdentityLookupLimitException si el dispositivo o la IP agotaron sus intentos
     */
    public IdentityVerification verify(IdentityDocument document, String firstName, String lastName,
                                       SignUpOrigin origin) {
        var now = LocalDateTime.now(clock);
        if (document.type() != DocumentType.DNI) {
            return IdentityVerification.of(IdentityVerificationStatus.NOT_APPLICABLE, now);
        }
        if (!enabled) {
            return IdentityVerification.of(IdentityVerificationStatus.PENDING, now);
        }

        var safeOrigin = origin == null ? SignUpOrigin.unknown() : origin;
        var deviceHash = hash("device:" + valueOrUnknown(safeOrigin.deviceId()));
        var ipHash = hash("ip:" + valueOrUnknown(safeOrigin.clientIp()));
        var dniHash = hash("dni:" + document.number());
        var attempt = new AttemptKeys(deviceHash, ipHash, dniHash, now);
        var window = now.minusHours(24);

        if (attemptRepository.countByDeviceHashAndOutcomeAndAttemptedAtAfter(
                deviceHash, IdentityLookupOutcome.MISMATCH, window) >= maxFailedPerDevice) {
            throw limited(attempt);
        }

        var cached = cachedName(dniHash, now);
        if (cached.isPresent()) {
            return compare(cached.get(), firstName, lastName, attempt, false);
        }

        // A partir de aqui cada intento gasta una consulta de la cuota.
        var dniAlreadyCounted = attemptRepository.existsByDeviceHashAndDniHashAndRemoteCallTrueAndAttemptedAtAfter(
                deviceHash, dniHash, window);
        if (!dniAlreadyCounted
                && attemptRepository.countDistinctRemoteDnisByDevice(deviceHash, window) >= maxDnisPerDevice) {
            throw limited(attempt);
        }
        if (attemptRepository.countByIpHashAndRemoteCallTrueAndAttemptedAtAfter(
                ipHash, now.minusHours(1)) >= maxLookupsPerIpHour) {
            throw limited(attempt);
        }
        if (attemptRepository.countByRemoteCallTrueAndAttemptedAtAfter(window) >= maxLookupsPerDay) {
            LOGGER.warn("Daily DNI lookup cap reached; account left pending verification");
            record(attempt, IdentityLookupOutcome.DAILY_CAP, false);
            return IdentityVerification.of(IdentityVerificationStatus.PENDING, now);
        }

        Optional<OfficialName> official;
        try {
            official = dniLookupPort.findByDni(document.number());
        } catch (DniLookupUnavailableException e) {
            // No se cuenta como consulta gastada: una caida del servicio no debe
            // restarle intentos al dispositivo de quien se estaba registrando.
            record(attempt, IdentityLookupOutcome.UNAVAILABLE, false);
            return IdentityVerification.of(IdentityVerificationStatus.PENDING, now);
        }
        if (official.isEmpty()) {
            // Puede ser un documento inexistente o un hueco de la fuente, que
            // admite errores. Bloquear aqui dejaria fuera a gente legitima; la
            // cuenta queda pendiente y se resuelve con el consentimiento firmado.
            record(attempt, IdentityLookupOutcome.NOT_FOUND, true);
            return IdentityVerification.of(IdentityVerificationStatus.PENDING, now);
        }

        cache.put(dniHash, new CachedName(official.get(), now.plusHours(cacheHours)));
        return compare(official.get(), firstName, lastName, attempt, true);
    }

    private IdentityVerification compare(OfficialName official, String firstName, String lastName,
                                         AttemptKeys attempt, boolean remoteCall) {
        var result = NameMatcher.compare(firstName, lastName, official);
        if (result == NameMatcher.Result.MISMATCH) {
            record(attempt, IdentityLookupOutcome.MISMATCH, remoteCall);
            throw new IdentityMismatchException();
        }
        record(attempt, IdentityLookupOutcome.MATCH, remoteCall);
        var status = result == NameMatcher.Result.FULL
                ? IdentityVerificationStatus.FULL_MATCH
                : IdentityVerificationStatus.PARTIAL_MATCH;
        return IdentityVerification.of(status, attempt.at());
    }

    private IdentityLookupLimitException limited(AttemptKeys attempt) {
        record(attempt, IdentityLookupOutcome.LIMITED, false);
        return new IdentityLookupLimitException();
    }

    private void record(AttemptKeys attempt, IdentityLookupOutcome outcome, boolean remoteCall) {
        attemptRecorder.record(new IdentityLookupAttempt(attempt.deviceHash(), attempt.ipHash(), attempt.dniHash(),
                outcome, remoteCall, attempt.at()));
    }

    private Optional<OfficialName> cachedName(String dniHash, LocalDateTime now) {
        var entry = cache.get(dniHash);
        if (entry == null) {
            return Optional.empty();
        }
        if (!now.isBefore(entry.expiresAt())) {
            cache.remove(dniHash);
            return Optional.empty();
        }
        return Optional.of(entry.name());
    }

    private String hash(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value.trim();
    }

    private record CachedName(OfficialName name, LocalDateTime expiresAt) {
    }

    private record AttemptKeys(String deviceHash, String ipHash, String dniHash, LocalDateTime at) {
    }
}
