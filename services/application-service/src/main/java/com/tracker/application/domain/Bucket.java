package com.tracker.application.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The four board columns. Wire value is lowercase snake_case
 * (applied | in_progress | complete | archived).
 */
public enum Bucket {
    APPLIED("applied"),
    IN_PROGRESS("in_progress"),
    COMPLETE("complete"),
    ARCHIVED("archived");

    private final String wire;

    Bucket(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static Bucket fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("bucket is required");
        }
        for (Bucket b : values()) {
            if (b.wire.equalsIgnoreCase(value) || b.name().equalsIgnoreCase(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown bucket: " + value);
    }
}
