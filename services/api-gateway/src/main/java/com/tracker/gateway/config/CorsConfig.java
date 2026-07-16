package com.tracker.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * Global CORS for the reactive gateway.
 *
 * <p>Allows the Vite frontend (http://localhost:5173) and browser-extension origins. Because
 * chrome-extension:// origins are dynamic per-install, the scaffold uses
 * {@code allowedOriginPatterns("*")} — TIGHTEN THIS IN PRODUCTION to the specific published
 * extension id(s) and the real frontend origin. Configured programmatically (not in yaml)
 * because allowedOriginPatterns with credentials cannot be expressed via the yaml globalcors
 * shorthand.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${CORS_ALLOWED_ORIGIN:http://localhost:5173}") String allowedOrigin) {

        CorsConfiguration config = new CorsConfiguration();
        // Explicit trusted origin (frontend) ...
        config.addAllowedOrigin(allowedOrigin);
        // ... plus pattern matching to cover chrome-extension:// (and other extension) origins.
        // SCAFFOLD: "*" is permissive; restrict to known extension ids in production.
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Ensure the Authorization (bearer) header is explicitly permitted.
        config.addAllowedHeader(HttpHeaders.AUTHORIZATION);
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
