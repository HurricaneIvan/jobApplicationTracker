package com.tracker.auth.exception;

/** Thrown when a signup targets an email that already has an account -> 409. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
