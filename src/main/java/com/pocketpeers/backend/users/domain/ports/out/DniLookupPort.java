package com.pocketpeers.backend.users.domain.ports.out;

import com.pocketpeers.backend.users.domain.exceptions.DniLookupUnavailableException;
import com.pocketpeers.backend.users.domain.model.valueobjects.OfficialName;

import java.util.Optional;

/**
 * Consulta del nombre que corresponde a un DNI en una fuente externa.
 */
public interface DniLookupPort {

    /**
     * @return el nombre del documento, o vacio si la fuente no lo encuentra
     * @throws DniLookupUnavailableException si la fuente no pudo responder
     */
    Optional<OfficialName> findByDni(String dni);
}
