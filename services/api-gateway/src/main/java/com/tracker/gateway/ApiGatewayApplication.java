package com.tracker.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Edge gateway for the job-application-tracker.
 *
 * Responsibilities: request routing to the internal services, HS256 JWT validation with
 * X-User-Id injection, per-user distributed rate limiting (Redis), CORS, and (in prod)
 * TLS termination. Reactive stack (Spring Cloud Gateway on WebFlux).
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
