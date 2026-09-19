package com.pocketpeers.backend.shared.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Exige credencial para escribir en {@code /api/v1/images}.
 *
 * <p>Ese endpoint esta en {@code permitAll()} a proposito: el servicio de OCR
 * sube la imagen procesada sin sesion de usuario, porque no es un usuario. La
 * consecuencia es que sin nada delante, cualquiera puede subir imagenes de
 * 10 MB hasta llenar la base o borrar recibos ajenos con un DELETE.</p>
 *
 * <p>Hasta ahora esa regla vivia en el {@code Caddyfile} de la VM. Al mover el
 * backend a un servicio gestionado desaparece el proxy, y con el la unica
 * proteccion que tenia. Esta clase la trae a Spring sin cambiar nada del OCR ni
 * del movil: los dos ya mandan lo que hace falta, lo unico que se mueve es
 * quien lo comprueba.</p>
 *
 * <p>De paso queda mas estricta que la version de Caddy. El proxy solo podia
 * mirar si la cabecera {@code Authorization} existia —cualquier valor pasaba—;
 * aqui se consulta el contexto de seguridad, que para entonces ya tiene el JWT
 * validado por {@code BearerAuthorizationRequestFilter}.</p>
 *
 * <p>El GET se deja abierto, igual que antes: los identificadores son UUID no
 * adivinables y la app los pide sin sesion.</p>
 */
public class ImageWriteAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageWriteAuthorizationFilter.class);
    private static final String IMAGES_PATH = "/api/v1/images";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final String serviceToken;
    private final boolean enforced;

    /**
     * @param serviceToken token compartido con el servicio de OCR; vacio lo desactiva
     * @param enforced     si false, solo advierte y deja pasar (desarrollo local,
     *                     donde no hay proxy ni token y el OCR corre en la misma red)
     */
    public ImageWriteAuthorizationFilter(String serviceToken, boolean enforced) {
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
        this.enforced = enforced;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(IMAGES_PATH)) {
            return true;
        }
        String method = request.getMethod();
        return !HttpMethod.POST.matches(method) && !HttpMethod.DELETE.matches(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (hasAuthenticatedUser() || hasValidServiceToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!enforced) {
            LOGGER.warn("Escritura de imagen sin credencial permitida porque no hay token de servicio "
                    + "configurado. method={}, uri={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        LOGGER.warn("Escritura de imagen rechazada por falta de credencial. method={}, uri={}",
                request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Se requiere credencial para escribir imagenes\"}");
    }

    private boolean hasAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * Comparacion en tiempo constante.
     *
     * <p>Un {@code equals} normal sale en cuanto encuentra el primer byte
     * distinto, y ese tiempo permite ir adivinando el token caracter a caracter.
     * Con un secreto de 32 bytes el ataque es poco practico, pero la alternativa
     * correcta cuesta una linea.</p>
     */
    private boolean hasValidServiceToken(HttpServletRequest request) {
        if (!StringUtils.hasText(serviceToken)) {
            return false;
        }
        String provided = request.getHeader(SERVICE_TOKEN_HEADER);
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.trim().getBytes(StandardCharsets.UTF_8),
                serviceToken.getBytes(StandardCharsets.UTF_8));
    }
}
