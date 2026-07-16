package com.tracker.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code jwt.*} config keys (see application.yml / CONTRACT.md). */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Shared HS256 secret (>= 32 bytes). Must match every validating service. */
    private String secret;

    /** Access token lifetime in seconds. */
    private long accessTtlSeconds = 900;

    /** Opaque refresh token lifetime in seconds. */
    private long refreshTtlSeconds = 1_209_600;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
    public void setAccessTtlSeconds(long accessTtlSeconds) { this.accessTtlSeconds = accessTtlSeconds; }

    public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
    public void setRefreshTtlSeconds(long refreshTtlSeconds) { this.refreshTtlSeconds = refreshTtlSeconds; }
}
