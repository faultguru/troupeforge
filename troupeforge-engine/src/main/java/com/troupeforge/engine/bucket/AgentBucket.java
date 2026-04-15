package com.troupeforge.engine.bucket;

import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ProviderConfig;
import com.troupeforge.core.persona.AgentProfile;

import com.troupeforge.core.id.PersonaId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentBucket(
    AgentBucketId bucketId,
    OrganizationId organizationId,
    StageContext stage,
    Map<AgentId, ResolvedAgent> resolvedAgents,
    Map<AgentProfileId, AgentProfile> agentProfiles,
    ModelConfig modelConfig,
    Map<String, ProviderConfig> providerConfigs,
    List<PromptSection> systemPromptSections,
    Instant loadedAt,
    String configVersion
) {

    /**
     * Backward-compatible constructor without providerConfigs.
     */
    public AgentBucket(
        AgentBucketId bucketId,
        OrganizationId organizationId,
        StageContext stage,
        Map<AgentId, ResolvedAgent> resolvedAgents,
        Map<AgentProfileId, AgentProfile> agentProfiles,
        ModelConfig modelConfig,
        List<PromptSection> systemPromptSections,
        Instant loadedAt,
        String configVersion
    ) {
        this(bucketId, organizationId, stage, resolvedAgents, agentProfiles,
             modelConfig, Map.of(), systemPromptSections, loadedAt, configVersion);
    }

    /**
     * Finds an agent profile by persona ID alone (persona IDs are unique within a bucket).
     */
    public Map.Entry<AgentProfileId, AgentProfile> findProfileByPersonaId(PersonaId personaId) {
        return agentProfiles.entrySet().stream()
                .filter(e -> e.getKey().personaId().equals(personaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No profile found for persona: " + personaId.value()));
    }
}
