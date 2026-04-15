package com.troupeforge.core.message;

import java.util.UUID;

public record MessageId(String value) {
    public static MessageId generate() {
        return new MessageId(UUID.randomUUID().toString());
    }
}
