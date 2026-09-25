package com.pocketpeers.backend.users.infrastructure.feign.decolecta.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Respuesta de {@code GET /v1/reniec/dni} de Decolecta. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DecolectaDniResponse(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("first_last_name") String firstLastName,
        @JsonProperty("second_last_name") String secondLastName,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("document_number") String documentNumber
) {
}
