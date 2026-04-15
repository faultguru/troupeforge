package com.troupeforge.core.id;

import java.util.UUID;

public record RequestId(String value) {
    public static RequestId generate() {
        return new RequestId(UUID.randomUUID().toString());
    }
}
