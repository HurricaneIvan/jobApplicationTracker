package com.tracker.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes the authenticated userId for each request.
 *
 * Trust model (see CONTRACT.md):
 *  - If the request carries a Bearer token, re-validate it here and use its subject.
 *  - Otherwise, if the gateway already validated and forwarded X-User-Id, trust that.
 * The client can never supply userId in the body — it always comes from one of these.
 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String userId = resolveUserId(request);
            if (userId == null || userId.isBlank()) {
                unauthorized(response, "Missing or invalid credentials");
                return;
            }
            UserContext.set(userId);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            unauthorized(response, "Invalid token");
        } finally {
            UserContext.clear();
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return jwtService.extractUserId(auth.substring(7).trim());
        }
        return request.getHeader("X-User-Id");   // trusted only when set by the gateway
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
