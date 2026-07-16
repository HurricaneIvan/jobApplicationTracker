package com.tracker.portal.exception;

import com.tracker.portal.dto.PortalDtos.PortalResponse;

/** Thrown on a duplicate domain. Carries the existing portal so the handler can return it in 409.data. */
public class DuplicatePortalException extends RuntimeException {

    private final transient PortalResponse existing;

    public DuplicatePortalException(PortalResponse existing) {
        super("Portal already exists");
        this.existing = existing;
    }

    public PortalResponse getExisting() {
        return existing;
    }
}
