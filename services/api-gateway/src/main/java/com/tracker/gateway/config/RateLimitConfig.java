package com.tracker.gateway.config;

import com.tracker.gateway.security.JwtValidationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate-limiting beans for the {@code RequestRateLimiter} gateway filter.
 *
 * <ul>
 *   <li>{@link #userKeyResolver()} keys the limiter by the resolved user (the trusted
 *       {@code X-User-Id} injected by {@link JwtValidationFilter}). For the public auth
 *       routes there is no user yet, so it falls back to the client IP.</li>
 *   <li>{@link #redisRateLimiter(int)} derives replenish/burst from the single
 *       {@code RATE_LIMIT_REQUESTS_PER_MINUTE} env var (per-second replenish ~= RPM / 60,
 *       burst = the full per-minute allowance).</li>
 * </ul>
 */
@Configuration
public class RateLimitConfig {

    /** Bean id referenced from application.yml as {@code #{@userKeyResolver}}. */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders()
                    .getFirst(JwtValidationFilter.USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // Fallback for unauthenticated (auth) routes: limit by client IP.
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * Replaces the auto-configured RedisRateLimiter so the whole budget is driven by one
     * env var. RATE_LIMIT_REQUESTS_PER_MINUTE=120 -> replenishRate 2/s, burstCapacity 120.
     */
    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter(
            @Value("${rate-limit.requests-per-minute:120}") int requestsPerMinute) {
        int replenishRate = Math.max(1, requestsPerMinute / 60);
        int burstCapacity = Math.max(replenishRate, requestsPerMinute);
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}
