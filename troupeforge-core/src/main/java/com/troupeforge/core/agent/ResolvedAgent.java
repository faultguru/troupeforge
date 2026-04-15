package com.troupeforge.core.agent;

import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.CapabilityId;
import com.troupeforge.core.id.ContractCapabilityId;
import com.troupeforge.core.id.GuardrailId;
import com.troupeforge.core.id.ToolId;

import java.util.List;
import java.util.Set;

public record ResolvedAgent(
    AgentId id,
    String name,
    String description,
    AgentType type,
    AgentId parent,
    List<AgentId> ancestorChain,
    Set<CapabilityId> capabilities,
    Set<GuardrailId> guardrails,
    Set<ToolId> tools,
    Set<ContractCapabilityId> contractCapabilities,
    List<PromptSection> promptSections,
    DirectReturnPolicy directReturnPolicy,
    int maxConcurrency
) {
}
