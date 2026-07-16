package com.tracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request/response DTOs, kept strictly separate from the JPA entities.
 * These are app-account credentials only — never third-party portal credentials.
 */
public final class AuthDtos {

    private AuthDtos() { }

    /** POST /signup body. */
    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 200) String password
    ) { }

    /** POST /login body. */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) { }

    /** POST /refresh body. */
    public record RefreshRequest(@NotBlank String refreshToken) { }

    /** POST /logout body. */
    public record LogoutRequest(@NotBlank String refreshToken) { }

    /** Response for signup / login / refresh — the token pair plus identity. */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String userId,
            String email
    ) { }
}
