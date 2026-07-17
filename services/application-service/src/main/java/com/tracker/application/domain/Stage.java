package com.tracker.application.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Sub-stage within the IN_PROGRESS bucket. Nullable on the entity (null = no sub-stage).
 * Wire value is lowercase (assessment | interviewing).
 */
public enum Stage {
    ASSESSMENT("assessment"),
    INTERVIEWING("interviewing");

    private final String wire;

    Stage(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /**
     * Parse a wire value to a Stage. Blank/null returns null (clears the sub-stage) rather
     * than throwing, so callers can clear it with an empty body.
     */
    @JsonCreator
    public static Stage fromWire(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) {
            return null;
        }
        for (Stage s : values()) {
            if (s.wire.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown stage: " + value);
    }
}
