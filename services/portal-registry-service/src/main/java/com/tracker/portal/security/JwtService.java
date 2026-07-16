package com.tracker.portal.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/** Validates HS256 access tokens issued by auth-service and extracts the userId (sub). */
@Component
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the userId (subject) if the token is a valid, unexpired access token. */
    public String extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object type = claims.get("type");
        if (type != null && !"access".equals(type.toString())) {
            throw new IllegalArgumentException("Not an access token");
        }
        return claims.getSubject();
    }
}
