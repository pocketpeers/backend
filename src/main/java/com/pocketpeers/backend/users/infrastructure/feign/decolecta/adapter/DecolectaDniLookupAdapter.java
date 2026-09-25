package com.pocketpeers.backend.users.infrastructure.feign.decolecta.adapter;

import com.pocketpeers.backend.users.domain.exceptions.DniLookupUnavailableException;
import com.pocketpeers.backend.users.domain.model.valueobjects.OfficialName;
import com.pocketpeers.backend.users.domain.ports.out.DniLookupPort;
import com.pocketpeers.backend.users.infrastructure.feign.decolecta.client.DecolectaDniClient;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consulta de DNI contra Decolecta.
 *
 * <p>Distingue dos clases de fallo, porque el registro reacciona distinto a
 * cada una. Un documento que la fuente no encuentra es una respuesta: se
 * devuelve vacio. Un servicio que no contesta —caido, sin token, sin cuota— no
 * dice nada sobre el documento: se lanza {@link DniLookupUnavailableException}.
 * En ambos casos la cuenta queda pendiente y el registro sigue; lo que cambia
 * es lo que queda anotado.</p>
 *
 * <p>El token se lee de una variable de entorno y nunca aparece en el codigo ni
 * en los registros.</p>
 */
@Component
public class DecolectaDniLookupAdapter implements DniLookupPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecolectaDniLookupAdapter.class);

    private final DecolectaDniClient client;
    private final String token;

    public DecolectaDniLookupAdapter(DecolectaDniClient client,
                                     @Value("${identity-verification.decolecta.token:}") String token) {
        this.client = client;
        this.token = token;
    }

    @Override
    public Optional<OfficialName> findByDni(String dni) {
        if (token == null || token.isBlank()) {
            throw new DniLookupUnavailableException("Decolecta token is not configured", null);
        }
        try {
            var response = client.findByDni("Bearer " + token, dni);
            if (response == null || isBlank(response.firstName()) || isBlank(response.firstLastName())) {
                // Una respuesta sin nombre no sirve para comparar. Tratarla como
                // coincidencia fallida bloquearia a alguien por un dato que falta
                // en la fuente, no por algo que escribio mal.
                return Optional.empty();
            }
            return Optional.of(new OfficialName(
                    response.firstName(), response.firstLastName(), response.secondLastName()));
        } catch (FeignException e) {
            var status = e.status();
            if (status == 400 || status == 404 || status == 422) {
                return Optional.empty();
            }
            // 401/403 token invalido, 402/429 cuota, 5xx o -1 (tiempo de espera).
            LOGGER.warn("Decolecta DNI lookup unavailable. status={}", status);
            throw new DniLookupUnavailableException("Decolecta responded with status " + status, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
