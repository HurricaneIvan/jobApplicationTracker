package com.tracker.auth.service;

import com.tracker.auth.config.JwtProperties;
import com.tracker.auth.domain.RefreshToken;
import com.tracker.auth.domain.UserAccount;
import com.tracker.auth.exception.InvalidRefreshTokenException;
import com.tracker.auth.repository.RefreshTokenRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues HS256 access tokens and manages opaque refresh tokens.
 *
 * The access-token shape is FROZEN by CONTRACT.md and must stay byte-compatible with
 * {@code com.tracker.application.security.JwtService}:
 *   sub = userId (UUID string), email, type = "access", plus iat/exp.
 */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokens;
    private final JwtProperties props;
    private final SecretKey key;

    public TokenService(RefreshTokenRepository refreshTokens, JwtProperties props) {
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Mint a signed HS256 access token for the given account. */
    public String issueAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getAccessTtlSeconds());
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("type", "access")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(exp))
                .signWith(key)
                .compact();
    }

    /** Create and persist a fresh opaque refresh token for the account. */
    public RefreshToken issueRefreshToken(UUID userId) {
        Instant now = Instant.now();
        RefreshToken rt = new RefreshToken(
                UUID.randomUUID(),
                randomOpaqueToken(),
                userId,
                now.plusSeconds(props.getRefreshTtlSeconds()),
                false,
                now);
        return refreshTokens.save(rt);
    }

    /**
     * Validate an opaque refresh token (exists, not revoked, not expired) and rotate it:
     * the presented token is revoked and a brand-new refresh token is issued.
     *
     * @return the newly issued refresh token.
     */
    public RefreshToken rotate(String presentedToken) {
        RefreshToken current = refreshTokens.findByToken(presentedToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        if (!current.isActive(Instant.now())) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        current.setRevoked(true);
        refreshTokens.save(current);
        return issueRefreshToken(current.getUserId());
    }

    /** Look up and validate an active refresh token, returning its owning userId. */
    public UUID activeUserId(String presentedToken) {
        RefreshToken rt = refreshTokens.findByToken(presentedToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        if (!rt.isActive(Instant.now())) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        return rt.getUserId();
    }

    /** Revoke a refresh token if it exists. Idempotent — unknown tokens are a no-op. */
    public void revoke(String presentedToken) {
        refreshTokens.findByToken(presentedToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokens.save(rt);
        });
    }

    private static String randomOpaqueToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
