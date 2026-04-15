package com.troupeforge.engine.bucket;

import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ProviderConfig;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.persona.PersonaDefinition;
import com.troupeforge.engine.config.AgentConfigLoader;
import com.troupeforge.engine.config.AgentInheritanceResolver;
import com.troupeforge.engine.config.ContractConfigLoader;
import com.troupeforge.engine.config.ModelConfigLoader;
import com.troupeforge.engine.config.PersonaComposer;
import com.troupeforge.engine.config.PersonaConfigLoader;
import com.troupeforge.engine.config.ProviderConfigLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentBucketLoaderImpl implements AgentBucketLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentBucketLoaderImpl.class);

    private final AgentConfigLoader agentConfigLoader;
    private final PersonaConfigLoader personaConfigLoader;
    private final ContractConfigLoader contractConfigLoader;
    private final AgentInheritanceResolver inheritanceResolver;
    private final PersonaComposer personaComposer;
    private final ModelConfigLoader modelConfigLoader;
    private final ProviderConfigLoader providerConfigLoader;

    /**
     * Backward-compatible constructor without ProviderConfigLoader.
     */
    public AgentBucketLoaderImpl(AgentConfigLoader agentConfigLoader,
                                    PersonaConfigLoader personaConfigLoader,
                                    ContractConfigLoader contractConfigLoader,
                                    AgentInheritanceResolver inheritanceResolver,
                                    PersonaComposer personaComposer,
                                    ModelConfigLoader modelConfigLoader) {
        this(agentConfigLoader, personaConfigLoader, contractConfigLoader,
             inheritanceResolver, personaComposer, modelConfigLoader, null);
    }

    public AgentBucketLoaderImpl(AgentConfigLoader agentConfigLoader,
                                    PersonaConfigLoader personaConfigLoader,
                                    ContractConfigLoader contractConfigLoader,
                                    AgentInheritanceResolver inheritanceResolver,
                                    PersonaComposer personaComposer,
                                    ModelConfigLoader modelConfigLoader,
                                    ProviderConfigLoader providerConfigLoader) {
        this.agentConfigLoader = Objects.requireNonNull(agentConfigLoader);
        this.personaConfigLoader = Objects.requireNonNull(personaConfigLoader);
        this.contractConfigLoader = Objects.requireNonNull(contractConfigLoader);
        this.inheritanceResolver = Objects.requireNonNull(inheritanceResolver);
        this.personaComposer = Objects.requireNonNull(personaComposer);
        this.modelConfigLoader = Objects.requireNonNull(modelConfigLoader);
        this.providerConfigLoader = providerConfigLoader;
    }

    @Override
    public AgentBucket load(OrganizationId org, StageContext stage, OrgConfigSource configSource) {
        log.info("Loading bucket from config source: org={}, stage={}", org.value(), stage);

        // 1. Load agent definitions
        var agentDefinitions = agentConfigLoader.loadAgentDefinitions(configSource);
        log.info("Agents found: {}", agentDefinitions.size());
        log.debug("Agent definitions: {}", agentDefinitions);

        // 2. Load persona definitions per agent
        Map<AgentId, List<PersonaDefinition>> personasByAgent =
            personaConfigLoader.loadPersonaDefinitions(configSource);

        // 3. Resolve inheritance
        List<ResolvedAgent> resolvedAgents = inheritanceResolver.resolve(agentDefinitions);

        // 4. Load contracts (stored for reference but not directly in bucket record)
        contractConfigLoader.loadContracts(configSource);

        // 5. Load model config
        ModelConfig modelConfig = modelConfigLoader.loadModelConfig(configSource);

        // 5b. Load provider configs
        Map<String, ProviderConfig> providerConfigs = Map.of();
        if (providerConfigLoader != null) {
            providerConfigs = providerConfigLoader.loadProviderConfigs(configSource);
            log.info("Provider configs loaded: {}", providerConfigs.size());
        }

        // 6. Build resolved agents map
        Map<AgentId, ResolvedAgent> resolvedAgentMap = new HashMap<>();
        for (ResolvedAgent resolved : resolvedAgents) {
            resolvedAgentMap.put(resolved.id(), resolved);
        }

        // 7. For each resolved agent + each persona, compose AgentProfile
        Map<AgentProfileId, AgentProfile> agentProfiles = new HashMap<>();
        for (ResolvedAgent resolved : resolvedAgents) {
            List<PersonaDefinition> personas = personasByAgent.get(resolved.id());
            if (personas == null || personas.isEmpty()) {
                continue;
            }
            for (PersonaDefinition persona : personas) {
                if (persona.disabled()) {
                    continue;
                }
                AgentProfile profile = personaComposer.compose(resolved, persona);
                agentProfiles.put(profile.profileId(), profile);
                log.debug("Loaded persona: profileId={}, personaId={}", profile.profileId().toKey(), persona.id().value());
            }
        }

        log.info("Personas loaded: {}", agentProfiles.size());

        // 8. Build and return AgentBucket
        AgentBucketId bucketId = AgentBucketId.of(org, stage);
        String configVersion = configSource.configVersion().orElse("unknown");

        return new AgentBucket(
            bucketId,
            org,
            stage,
            resolvedAgentMap,
            agentProfiles,
            modelConfig,
            providerConfigs,
            List.of(),  // system prompt sections are empty; they come from root agent's inheritance
            Instant.now(),
            configVersion
        );
    }
}
