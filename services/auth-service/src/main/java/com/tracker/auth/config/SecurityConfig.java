package com.tracker.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wiring for password hashing and JWT config binding.
 *
 * NOTE: this pulls in ONLY spring-security-crypto (BCrypt). There is no security
 * filter chain — every /api/v1/auth/** endpoint is public, since these endpoints
 * are how clients obtain a token in the first place.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
