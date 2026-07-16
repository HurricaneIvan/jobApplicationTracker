package com.tracker.auth.service;

import com.tracker.auth.domain.RefreshToken;
import com.tracker.auth.domain.UserAccount;
import com.tracker.auth.dto.AuthDtos.AuthResponse;
import com.tracker.auth.dto.AuthDtos.LoginRequest;
import com.tracker.auth.dto.AuthDtos.SignupRequest;
import com.tracker.auth.exception.DuplicateEmailException;
import com.tracker.auth.exception.InvalidCredentialsException;
import com.tracker.auth.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * App-account authentication: signup, login, refresh-token rotation and logout.
 * Stores app credentials only — never any third-party job-portal credentials.
 */
@Service
public class AuthService {

    private final UserAccountRepository users;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository users, TokenService tokenService,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------ signup
    @Transactional
    public AuthResponse signup(SignupRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(req.password()),
                Instant.now());
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent signup on the unique email constraint.
            throw new DuplicateEmailException("An account with this email already exists");
        }

        return issuePair(user);
    }

    // ------------------------------------------------------------------- login
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        UserAccount user = users.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return issuePair(user);
    }

    // ----------------------------------------------------------------- refresh
    @Transactional
    public AuthResponse refresh(String presentedRefreshToken) {
        // Resolve the owning account first (validates the token), then rotate.
        UUID userId = tokenService.activeUserId(presentedRefreshToken);
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));

        RefreshToken rotated = tokenService.rotate(presentedRefreshToken);
        String access = tokenService.issueAccessToken(user);
        return new AuthResponse(access, rotated.getToken(), user.getId().toString(), user.getEmail());
    }

    // ------------------------------------------------------------------ logout
    @Transactional
    public void logout(String presentedRefreshToken) {
        tokenService.revoke(presentedRefreshToken);
    }

    // ------------------------------------------------------------------ helpers
    private AuthResponse issuePair(UserAccount user) {
        String access = tokenService.issueAccessToken(user);
        RefreshToken refresh = tokenService.issueRefreshToken(user.getId());
        return new AuthResponse(access, refresh.getToken(), user.getId().toString(), user.getEmail());
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
