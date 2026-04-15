package com.troupeforge.core.id;

import java.util.UUID;

public record AgentSessionId(String value) {
    public static AgentSessionId generate() {
        return new AgentSessionId(UUID.randomUUID().toString());
    }
}
