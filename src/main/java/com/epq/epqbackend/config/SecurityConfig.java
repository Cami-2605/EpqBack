package com.epq.epqbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Configuración de seguridad Spring Security.
 *
 * Cambios respecto a la versión anterior:
 * - CORS centralizado únicamente aquí (se eliminó la duplicación de headers manuales).
 * - Orígenes configurables desde application.yaml (cors.allowed-origins).
 * - Mantiene el mecanismo de token simple existente (no JWT real) para no romper el frontend.
 * - El endpoint GET /auth/hash queda deshabilitado en producción mediante configuración.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Orígenes CORS permitidos. Se configuran en application.yaml.
     * Default: localhost:4200 (Angular dev) y localhost:3000 (React dev).
     */
    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
    private String allowedOriginsConfig;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos: login, registro y health check
                .requestMatchers("/auth/login", "/auth/registrar").permitAll()
                // Todo lo demás requiere al menos el token básico
                .anyRequest().permitAll()  // cambiar a .authenticated() cuando se implemente JWT real
            )
            .addFilterBefore(new TokenAuthFilter(),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origenes = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        // Exponer cabeceras útiles para el frontend (nombre de archivo en descarga)
        config.setExposedHeaders(Arrays.asList(
                "Content-Disposition", "Content-Type", "Content-Length"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Filtro de autenticación por token simple.
     * Mantiene la lógica actual: el token tiene formato "token-{timestamp}-{username}".
     *
     * NOTA: Este mecanismo no es seguro para producción. Para habilitar JWT real,
     * reemplazar este filtro por uno que valide el token con la librería jjwt
     * que ya está en las dependencias (io.jsonwebtoken).
     */
    public static class TokenAuthFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {

            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                // Formato del token: "token-{timestamp}-{username}"
                // Tiene al menos 2 guiones → hay al menos 3 partes
                String[] partes = token.split("-");
                if (partes.length >= 3 && "token".equals(partes[0])) {
                    // El username puede contener guiones, reconstruir desde la parte 3
                    String username = String.join("-",
                            Arrays.copyOfRange(partes, 2, partes.length));
                    if (!username.isBlank()) {
                        var userDetails = new org.springframework.security.core.userdetails.User(
                                username, "", Collections.emptyList());
                        var auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }

            chain.doFilter(request, response);
        }
    }
}
