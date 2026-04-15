package com.troupeforge.core.agent;

import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.CapabilityId;
import com.troupeforge.core.id.ContractCapabilityId;
import com.troupeforge.core.id.GuardrailId;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.id.ToolId;

import java.util.List;

public record AgentDefinition(
    AgentId id,
    String name,
    String description,
    AgentType type,
    AgentId parent,
    InheritableSet<CapabilityId> capabilities,
    InheritableSet<GuardrailId> guardrails,
    InheritableSet<ToolId> tools,
    InheritableSet<ContractCapabilityId> contractCapabilities,
    InheritablePromptSections promptSections,
    List<PersonaSectionDefinition> personaSections,
    List<TierId> allowedTiers,
    DirectReturnPolicy directReturnPolicy,
    int maxConcurrency
) {
}
