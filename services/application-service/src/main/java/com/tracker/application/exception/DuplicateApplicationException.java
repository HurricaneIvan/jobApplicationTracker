package com.tracker.application.exception;

import com.tracker.application.dto.ApplicationDtos.ApplicationResponse;

/** Thrown on a dedup collision. Carries the existing tile so the handler can return it in 409.data. */
public class DuplicateApplicationException extends RuntimeException {

    private final transient ApplicationResponse existing;

    public DuplicateApplicationException(ApplicationResponse existing) {
        super("Application already exists");
        this.existing = existing;
    }

    public ApplicationResponse getExisting() {
        return existing;
    }
}
