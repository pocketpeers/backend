package com.pocketpeers.backend.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * La regla que antes vivia en el Caddyfile, ahora comprobable.
 *
 * <p>Estaba escrita en la configuracion de un proxy, donde no habia forma de
 * verificarla sin desplegar. Aqui se prueba cada camino: el movil con sesion,
 * el OCR con su token, y el que no trae nada.</p>
 */
class ImageWriteAuthorizationFilterTests {

    private static final String TOKEN = "3f1c9b7a2e5d48c6a0b1d2e3f4a5b6c7";

    private final FilterChain chain = Mockito.mock(FilterChain.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, boolean enforced) throws Exception {
        var response = new MockHttpServletResponse();
        new ImageWriteAuthorizationFilter(TOKEN, enforced).doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest write(String method) {
        var request = new MockHttpServletRequest(method, "/api/v1/images");
        request.setRequestURI("/api/v1/images");
        return request;
    }

    @Test
    @DisplayName("POST sin credencial se rechaza con 403")
    void rechazaPostAnonimo() throws Exception {
        assertThat(run(write("POST"), true).getStatus()).isEqualTo(403);
        Mockito.verify(chain, Mockito.never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("DELETE sin credencial se rechaza: borrar recibos ajenos era el riesgo mayor")
    void rechazaDeleteAnonimo() throws Exception {
        assertThat(run(write("DELETE"), true).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("El OCR pasa con su token de servicio")
    void aceptaTokenDeServicio() throws Exception {
        var request = write("POST");
        request.addHeader("X-Service-Token", TOKEN);
        assertThat(run(request, true).getStatus()).isEqualTo(200);
        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Un token equivocado no pasa")
    void rechazaTokenIncorrecto() throws Exception {
        var request = write("POST");
        request.addHeader("X-Service-Token", "otra-cosa");
        assertThat(run(request, true).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("El movil pasa con su sesion, sin token de servicio")
    void aceptaUsuarioAutenticado() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("salvador", null, List.of()));
        assertThat(run(write("POST"), true).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("El GET sigue abierto: los UUID no son adivinables y la app los pide sin sesion")
    void dejaPasarGet() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/images/abc");
        request.setRequestURI("/api/v1/images/abc");
        assertThat(run(request, true).getStatus()).isEqualTo(200);
        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Otros endpoints no se tocan")
    void noTocaOtrosEndpoints() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/expenses");
        request.setRequestURI("/api/v1/expenses");
        assertThat(run(request, true).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sin exigir (desarrollo local) deja pasar pero avisa")
    void modoDesarrolloDejaPasar() throws Exception {
        assertThat(run(write("POST"), false).getStatus()).isEqualTo(200);
    }
}
