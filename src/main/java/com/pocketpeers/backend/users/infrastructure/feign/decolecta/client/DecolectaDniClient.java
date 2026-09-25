package com.pocketpeers.backend.users.infrastructure.feign.decolecta.client;

import com.pocketpeers.backend.users.infrastructure.feign.decolecta.dto.DecolectaDniResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "decolecta-dni-client",
        url = "${identity-verification.decolecta.url:https://api.decolecta.com}",
        configuration = DecolectaFeignConfiguration.class)
public interface DecolectaDniClient {

    @GetMapping("/v1/reniec/dni")
    DecolectaDniResponse findByDni(@RequestHeader("Authorization") String authorization,
                                   @RequestParam("numero") String dni);
}
