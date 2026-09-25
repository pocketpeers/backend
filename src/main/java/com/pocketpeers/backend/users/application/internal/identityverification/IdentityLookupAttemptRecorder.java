package com.pocketpeers.backend.users.application.internal.identityverification;

import com.pocketpeers.backend.users.domain.model.entities.IdentityLookupAttempt;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.IdentityLookupAttemptRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

/**
 * Guarda cada intento de verificacion en su propia transaccion.
 *
 * <p>Es una clase aparte por una razon concreta. El alta corre dentro de una
 * transaccion, y un nombre que no coincide termina lanzando una excepcion que
 * la revierte entera. Si el intento se guardara dentro de esa misma
 * transaccion, se revertiria con ella: los intentos fallidos nunca quedarian
 * contados y el tope de cinco al dia no frenaria a nadie. Con
 * {@code REQUIRES_NEW} el intento se confirma por su lado, pase lo que pase con
 * el alta. Y tiene que ser otro bean para que el proxy de Spring aplique la
 * anotacion: una llamada dentro de la misma clase la ignoraria.</p>
 */
@Component
public class IdentityLookupAttemptRecorder {

    private final IdentityLookupAttemptRepository repository;

    public IdentityLookupAttemptRecorder(IdentityLookupAttemptRepository repository) {
        this.repository = repository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(IdentityLookupAttempt attempt) {
        repository.save(attempt);
    }
}
