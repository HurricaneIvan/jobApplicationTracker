package com.tracker.auth.exception;

/** Thrown when a refresh token is unknown, revoked, or expired -> 401. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
