package com.tonalli.web.security;

import java.util.LinkedHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Configuración central de seguridad. Principio: todo está prohibido salvo lo que se permite
 * explícitamente (anyRequest().authenticated()).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** BCrypt con costo 12: unos 250 ms por contraseña, suficiente para frenar ataques de fuerza bruta. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa al registro cuando una sesión termina, para que no queden sesiones fantasma. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {

        // Login fallido: mismo destino para todo fallo, salvo "correo sin verificar", que solo ocurre
        // cuando la contraseña ya fue correcta.
        AuthenticationFailureHandler alFallar = (request, response, excepcion) -> {
            String destino = excepcion instanceof CredentialsExpiredException ? "/login?noverificado" : "/login?error";
            response.sendRedirect(request.getContextPath() + destino);
        };

        // Las rutas /api/ responden 401 en JSON; las páginas redirigen al formulario de login.
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> puntosDeEntrada = new LinkedHashMap<>();
        puntosDeEntrada.put(request -> request.getRequestURI().startsWith("/api/"),
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint puntoDeEntrada = new DelegatingAuthenticationEntryPoint(puntosDeEntrada);
        puntoDeEntrada.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/error", "/error/**").permitAll()
                .requestMatchers("/login", "/registro", "/registro/**", "/verificar").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/ofertas", "/ofertas/{id:\\d+}",
                        "/api/ofertas", "/api/ofertas/{id:\\d+}").permitAll()
                .requestMatchers("/moderacion/**").hasAnyRole("MODERADOR", "ADMIN")
                .requestMatchers("/admin/**", "/api/estadisticas/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("correo")
                .passwordParameter("password")
                .defaultSuccessUrl("/", false)
                .failureHandler(alFallar)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?salio")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(e -> e.authenticationEntryPoint(puntoDeEntrada))
            .sessionManagement(s -> s
                .sessionConcurrency(c -> c.maximumSessions(5).sessionRegistry(sessionRegistry)))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
                                + "form-action 'self'; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));
        // CSRF queda activado por defecto: todo POST necesita el token (los formularios lo llevan oculto).

        return http.build();
    }
}
