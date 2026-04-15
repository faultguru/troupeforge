package com.troupeforge.core.persona;

import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.id.AgentProfileId;

import java.util.List;

public record AgentProfile(
    AgentProfileId profileId,
    ResolvedAgent agent,
    PersonaDefinition persona,
    String effectiveDisplayName,
    String effectiveAvatar,
    List<PromptSection> effectivePromptSections,
    List<TierId> allowedTiers
) {
}
