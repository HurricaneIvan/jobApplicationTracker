package com.tracker.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Global filter that validates the HS256 access token (per CONTRACT.md) for every request
 * EXCEPT the public auth routes and actuator, then injects the trusted {@code X-User-Id}
 * header (token subject) into the forwarded request.
 *
 * <p>Trust model: any client-supplied {@code X-User-Id} is ALWAYS stripped here first, so the
 * only X-User-Id ever seen downstream is the one this filter derives from a verified token.
 * Downstream services trust X-User-Id precisely because it can only originate at the gateway.
 *
 * <p>Ordered before the per-route {@code RequestRateLimiter} so the injected X-User-Id is
 * available to the rate-limiter's KeyResolver.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;

    public JwtValidationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Public paths: forward as-is but still strip any spoofed X-User-Id.
        if (isPublic(path)) {
            return chain.filter(stripUserId(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        final String userId;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object type = claims.get("type");
            if (type != null && !"access".equals(type.toString())) {
                return unauthorized(exchange, "Not an access token");
            }
            userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                return unauthorized(exchange, "Token has no subject");
            }
        } catch (Exception e) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        // Strip any client-supplied X-User-Id, then inject the verified one.
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> h.remove(USER_ID_HEADER))
                .header(USER_ID_HEADER, userId)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static boolean isPublic(String path) {
        return path.startsWith("/api/v1/auth/") || path.startsWith("/actuator/");
    }

    /** Remove any client-supplied X-User-Id so it can never be spoofed downstream. */
    private static ServerWebExchange stripUserId(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> h.remove(USER_ID_HEADER))
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                + message.replace("\"", "'") + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        // Run before the RequestRateLimiter (route filters start at order >= 1) so the
        // KeyResolver can read the injected X-User-Id.
        return -1;
    }
}
