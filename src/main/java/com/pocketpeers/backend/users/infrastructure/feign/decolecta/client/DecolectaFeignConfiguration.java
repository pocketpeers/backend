package com.pocketpeers.backend.users.infrastructure.feign.decolecta.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Tiempos de espera del cliente de Decolecta.
 *
 * <p>Sin anotar con {@code @Configuration} a proposito: asi solo aplica al
 * cliente que la nombra y no a los demas clientes Feign del proyecto.</p>
 *
 * <p>Van en codigo y no en las propiedades porque los valores por defecto de
 * Feign —10 s para conectar y 60 s para leer— dejarian a una persona mirando la
 * pantalla de registro un minuto entero si el servicio se cuelga. Con 3 y 5
 * segundos, lo peor que pasa es que la cuenta quede pendiente de verificar.</p>
 */
public class DecolectaFeignConfiguration {

    @Bean
    public Request.Options decolectaRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }
}
