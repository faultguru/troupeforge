package com.troupeforge.core.id;

public record AgentProfileId(AgentId agentId, PersonaId personaId) {
    public String toKey() {
        return agentId.value() + ":" + personaId.value();
    }
}
