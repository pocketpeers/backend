package com.pocketpeers.backend.users.infrastructure.authorization.sfs.configuration;

import com.pocketpeers.backend.shared.infrastructure.security.ImageWriteAuthorizationFilter;
import com.pocketpeers.backend.users.infrastructure.authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import com.pocketpeers.backend.users.infrastructure.hashing.bcypt.BCryptHashingService;
import com.pocketpeers.backend.users.infrastructure.tokens.jwt.BearerTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;


/**
 * Web Security Configuration.
 * <p>
 * This class is responsible for configuring the web security.
 * It enables the method security and configures the security filter chain.
 * It includes the authentication manager, the authentication provider, the password encoder and the authentication entry point.
 * </p>
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final UserDetailsService userDetailsService;

    private final BearerTokenService tokenService;

    private final BCryptHashingService hashingService;

    private final AuthenticationEntryPoint unauthorizedRequestHandler;

    /**
     * This method creates the Bearer Authorization Request Filter.
     * @return The Bearer Authorization Request Filter
     */
    /**
     * Token compartido con el servicio de OCR. Vacio en desarrollo local.
     *
     * <p>Se llama igual que la variable de entorno {@code SERVICE_TOKEN} que ya
     * usan el Caddyfile y el Space de Hugging Face, para que las tres puntas
     * lean el mismo valor sin traducciones de nombre por el camino.</p>
     */
    @Value("${app.images.service-token:${SERVICE_TOKEN:}}")
    private String imageServiceToken;

    /**
     * Fuera de desarrollo la credencial es obligatoria.
     *
     * <p>En local no hay proxy ni token y el OCR corre en la misma red, asi que
     * exigirlo obligaria a configurarlo solo para levantar el proyecto. Con un
     * perfil desplegado el endpoint es publico y ahi no se negocia: si falta el
     * token la escritura se rechaza, en vez de quedar abierta en silencio.</p>
     */
    @Bean
    public ImageWriteAuthorizationFilter imageWriteAuthorizationFilter(Environment environment) {
        boolean development = List.of(environment.getActiveProfiles()).contains("dev")
                || environment.getActiveProfiles().length == 0;
        boolean enforced = !development || StringUtils.hasText(imageServiceToken);
        return new ImageWriteAuthorizationFilter(imageServiceToken, enforced);
    }

    @Bean
    public BearerAuthorizationRequestFilter authorizationRequestFilter() {
        return new BearerAuthorizationRequestFilter(tokenService, userDetailsService);
    }

    /**
     * This method creates the authentication manager.
     * @param authenticationConfiguration The authentication configuration
     * @return The authentication manager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * This method creates the authentication provider.
     * @return The authentication provider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(hashingService);
        return authenticationProvider;
    }

    /**
     * This method creates the password encoder.
     * @return The password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return hashingService;
    }

    /**
     * This method creates the security filter chain.
     * It also configures the http security.
     *
     * @param http The http security
     * @return The security filter chain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ImageWriteAuthorizationFilter imageWriteAuthorizationFilter)
            throws Exception {
        http.cors(configurer -> configurer.configurationSource(x -> {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of("*"));
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
            cors.setAllowedHeaders(List.of("*"));
            return cors;
        }));
        http.csrf(csrfConfigurer -> csrfConfigurer.disable())
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(unauthorizedRequestHandler))
                .sessionManagement( customizer -> customizer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        // El cambio de contrasena vive bajo /authentication pero exige
                        // sesion iniciada. Esta regla va ANTES del permitAll de abajo
                        // porque el primer patron que coincide es el que manda; si
                        // fuera despues, el comodin lo dejaria abierto al publico.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/authentication/password").authenticated()
                        .requestMatchers(
                                "/api/v1/authentication/**",
                                "/api/v1/images/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/webjars/**").permitAll()
                        .anyRequest().authenticated());
        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authorizationRequestFilter(), UsernamePasswordAuthenticationFilter.class);
        // Despues del filtro del JWT a proposito: para cuando corre, el contexto
        // de seguridad ya tiene al usuario resuelto y el filtro puede exigir una
        // sesion real en vez de limitarse a mirar si la cabecera existe.
        http.addFilterAfter(imageWriteAuthorizationFilter, BearerAuthorizationRequestFilter.class);
        return http.build();

    }

    /**
     * This is the constructor of the class.
     * @param userDetailsService The user details service
     * @param tokenService The token service
     * @param hashingService The hashing service
     * @param authenticationEntryPoint The authentication entry point
     */
    public WebSecurityConfiguration(@Qualifier("defaultUserDetailsService") UserDetailsService userDetailsService, BearerTokenService tokenService, BCryptHashingService hashingService, AuthenticationEntryPoint authenticationEntryPoint) {
        this.userDetailsService = userDetailsService;
        this.tokenService = tokenService;
        this.hashingService = hashingService;
        this.unauthorizedRequestHandler = authenticationEntryPoint;
    }
}