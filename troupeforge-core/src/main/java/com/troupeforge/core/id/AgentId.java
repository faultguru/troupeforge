package com.troupeforge.core.id;

public record AgentId(String value) {
    public static final AgentId ROOT = new AgentId("root");
}
