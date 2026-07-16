package com.tracker.auth.exception;

/** Thrown when a login presents an unknown email or a wrong password -> 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
