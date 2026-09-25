package com.pocketpeers.backend.users.application.internal.identityverification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.pocketpeers.backend.users.domain.exceptions.DniLookupUnavailableException;
import com.pocketpeers.backend.users.domain.exceptions.IdentityLookupLimitException;
import com.pocketpeers.backend.users.domain.exceptions.IdentityMismatchException;
import com.pocketpeers.backend.users.domain.model.entities.IdentityLookupAttempt;
import com.pocketpeers.backend.users.domain.model.valueobjects.DocumentType;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityDocument;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityLookupOutcome;
import com.pocketpeers.backend.users.domain.model.valueobjects.IdentityVerificationStatus;
import com.pocketpeers.backend.users.domain.model.valueobjects.OfficialName;
import com.pocketpeers.backend.users.domain.model.valueobjects.SignUpOrigin;
import com.pocketpeers.backend.users.domain.ports.out.DniLookupPort;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.IdentityLookupAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * La verificacion de DNI en el registro y los topes que protegen la cuota.
 *
 * <p>La fuente externa se simula: ninguna de estas pruebas gasta una consulta
 * real.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityVerificationServiceTests {

    @Mock private DniLookupPort dniLookupPort;
    @Mock private IdentityLookupAttemptRepository attemptRepository;
    @Mock private IdentityLookupAttemptRecorder attemptRecorder;

    private IdentityVerificationService service;

    private static final IdentityDocument DNI = new IdentityDocument(DocumentType.DNI, "71234567");
    private static final OfficialName ROSA = new OfficialName("ROSA MARIA", "QUISPE", "HUAMAN");
    private static final SignUpOrigin ORIGIN = new SignUpOrigin("dispositivo-1", "200.1.2.3");

    @BeforeEach
    void setUp() {
        service = new IdentityVerificationService(dniLookupPort, attemptRepository, attemptRecorder);
        // Los valores llegan por @Value, que no corre fuera de Spring.
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxDnisPerDevice", 3);
        ReflectionTestUtils.setField(service, "maxFailedPerDevice", 5);
        ReflectionTestUtils.setField(service, "maxLookupsPerIpHour", 20);
        ReflectionTestUtils.setField(service, "maxLookupsPerDay", 15);
        ReflectionTestUtils.setField(service, "cacheHours", 24);
        ReflectionTestUtils.setField(service, "hashSecret", "secreto-de-prueba");

        when(dniLookupPort.findByDni("71234567")).thenReturn(Optional.of(ROSA));
    }

    private IdentityLookupAttempt lastRecordedAttempt() {
        var captor = ArgumentCaptor.forClass(IdentityLookupAttempt.class);
        verify(attemptRecorder, org.mockito.Mockito.atLeastOnce()).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void aMatchingNameIsVerifiedAndSpendsOneLookup() {
        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PARTIAL_MATCH);
        verify(dniLookupPort, times(1)).findByDni("71234567");
        assertThat(lastRecordedAttempt().getOutcome()).isEqualTo(IdentityLookupOutcome.MATCH);
        assertThat(lastRecordedAttempt().isRemoteCall()).isTrue();
    }

    @Test
    void theWholeNameIsAFullMatch() {
        var result = service.verify(DNI, "Rosa María", "Quispe Huamán", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.FULL_MATCH);
    }

    @Test
    void aMismatchIsRejectedAndCounted() {
        assertThatThrownBy(() -> service.verify(DNI, "Rosa", "Flores", ORIGIN))
                .isInstanceOf(IdentityMismatchException.class);

        assertThat(lastRecordedAttempt().getOutcome()).isEqualTo(IdentityLookupOutcome.MISMATCH);
    }

    /**
     * El caso mas comun en la sesion presencial: equivocarse al escribir el
     * nombre y corregirlo. La correccion no debe gastar otra consulta.
     */
    @Test
    void correctingTheNameWithTheSameDniDoesNotSpendAnotherLookup() {
        assertThatThrownBy(() -> service.verify(DNI, "Rossa", "Quispe", ORIGIN))
                .isInstanceOf(IdentityMismatchException.class);

        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PARTIAL_MATCH);
        verify(dniLookupPort, times(1)).findByDni("71234567");
        assertThat(lastRecordedAttempt().isRemoteCall()).isFalse();
    }

    @Test
    void theCacheExpiresAfterItsWindow() {
        var start = Instant.parse("2026-10-06T15:00:00Z");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(start, ZoneId.of("America/Lima")));
        service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(start.plusSeconds(25 * 3600), ZoneId.of("America/Lima")));
        service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        verify(dniLookupPort, times(2)).findByDni("71234567");
    }

    @Test
    void aForeignerCardIsNotLookedUp() {
        var ce = new IdentityDocument(DocumentType.CE, "001234567");

        var result = service.verify(ce, "John", "Smith", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.NOT_APPLICABLE);
        verify(dniLookupPort, never()).findByDni(anyString());
    }

    @Test
    void whenDisabledNothingIsLookedUpAndTheAccountStaysPending() {
        ReflectionTestUtils.setField(service, "enabled", false);

        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PENDING);
        verify(dniLookupPort, never()).findByDni(anyString());
        verify(attemptRecorder, never()).record(any());
    }

    @Test
    void aDeviceWithFiveFailuresIsBlockedEvenForACachedDni() {
        when(attemptRepository.countByDeviceHashAndOutcomeAndAttemptedAtAfter(
                anyString(), eq(IdentityLookupOutcome.MISMATCH), any())).thenReturn(5L);

        assertThatThrownBy(() -> service.verify(DNI, "Rosa", "Quispe", ORIGIN))
                .isInstanceOf(IdentityLookupLimitException.class);

        verify(dniLookupPort, never()).findByDni(anyString());
        assertThat(lastRecordedAttempt().getOutcome()).isEqualTo(IdentityLookupOutcome.LIMITED);
    }

    @Test
    void aDeviceThatAlreadyTriedThreeDnisCannotTryAFourth() {
        when(attemptRepository.countDistinctRemoteDnisByDevice(anyString(), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.verify(DNI, "Rosa", "Quispe", ORIGIN))
                .isInstanceOf(IdentityLookupLimitException.class);

        verify(dniLookupPort, never()).findByDni(anyString());
    }

    /**
     * Si la cache se perdio por un reinicio, volver a consultar el mismo DNI no
     * es un documento nuevo y no debe contar como el cuarto.
     */
    @Test
    void aDniAlreadyCountedForTheDeviceIsNotANewOne() {
        when(attemptRepository.countDistinctRemoteDnisByDevice(anyString(), any())).thenReturn(3L);
        when(attemptRepository.existsByDeviceHashAndDniHashAndRemoteCallTrueAndAttemptedAtAfter(
                anyString(), anyString(), any())).thenReturn(true);

        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PARTIAL_MATCH);
    }

    @Test
    void anIpOverItsHourlyLimitIsBlocked() {
        when(attemptRepository.countByIpHashAndRemoteCallTrueAndAttemptedAtAfter(anyString(), any()))
                .thenReturn(20L);

        assertThatThrownBy(() -> service.verify(DNI, "Rosa", "Quispe", ORIGIN))
                .isInstanceOf(IdentityLookupLimitException.class);

        verify(dniLookupPort, never()).findByDni(anyString());
    }

    /** El tope global protege la cuota, pero no deja a nadie sin registrarse. */
    @Test
    void theDailyCapLeavesTheAccountPendingInsteadOfBlocking() {
        when(attemptRepository.countByRemoteCallTrueAndAttemptedAtAfter(any())).thenReturn(15L);

        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PENDING);
        verify(dniLookupPort, never()).findByDni(anyString());
        assertThat(lastRecordedAttempt().getOutcome()).isEqualTo(IdentityLookupOutcome.DAILY_CAP);
    }

    /** Una caida de la fuente no frena una sesion grupal. */
    @Test
    void anUnavailableSourceLeavesTheAccountPendingWithoutChargingTheDevice() {
        when(dniLookupPort.findByDni("71234567"))
                .thenThrow(new DniLookupUnavailableException("caido", null));

        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PENDING);
        assertThat(lastRecordedAttempt().getOutcome()).isEqualTo(IdentityLookupOutcome.UNAVAILABLE);
        assertThat(lastRecordedAttempt().isRemoteCall()).isFalse();
    }

    @Test
    void aDniTheSourceDoesNotFindLeavesTheAccountPending() {
        when(dniLookupPort.findByDni("71234567")).thenReturn(Optional.empty());

        var result = service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        assertThat(result.status()).isEqualTo(IdentityVerificationStatus.PENDING);
        assertThat(lastRecordedAttempt().getOutcome()).isEqualTo(IdentityLookupOutcome.NOT_FOUND);
    }

    @Test
    void theDniIsNeverStoredInClear() {
        service.verify(DNI, "Rosa", "Quispe", ORIGIN);

        var attempt = lastRecordedAttempt();
        assertThat(attempt.getDniHash()).doesNotContain("71234567").hasSize(64);
        assertThat(attempt.getDeviceHash()).doesNotContain("dispositivo-1");
        assertThat(attempt.getIpHash()).doesNotContain("200.1.2.3");
    }
}
