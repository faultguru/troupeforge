package com.troupeforge.core.message;

import java.util.UUID;

public record CorrelationId(String value) {
    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }
}
