package com.troupeforge.engine.config;

import com.troupeforge.core.agent.PersonaSectionDefinition;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.persona.PersonaDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Default implementation of {@link PersonaComposer} that merges a resolved agent's
 * prompt sections with persona-specific sections, tiers, and display settings.
 */
public class PersonaComposerImpl implements PersonaComposer {

    private final Function<AgentId, List<PersonaSectionDefinition>> personaSectionLookup;
    private final Function<AgentId, List<TierId>> agentTierLookup;

    /**
     * @param personaSectionLookup resolves an agent's persona section definitions by agent id
     * @param agentTierLookup      resolves an agent's allowed tiers by agent id
     */
    public PersonaComposerImpl(
            Function<AgentId, List<PersonaSectionDefinition>> personaSectionLookup,
            Function<AgentId, List<TierId>> agentTierLookup) {
        this.personaSectionLookup = personaSectionLookup;
        this.agentTierLookup = agentTierLookup;
    }

    @Override
    public AgentProfile compose(ResolvedAgent agent, PersonaDefinition persona) {
        List<PromptSection> sections = new ArrayList<>();

        // 1. Start with agent's prompt sections (sorted by order)
        if (agent.promptSections() != null) {
            List<PromptSection> agentSections = new ArrayList<>(agent.promptSections());
            agentSections.sort(Comparator.comparingInt(PromptSection::order));
            sections.addAll(agentSections);
        }

        // Build a lookup of persona section definitions by key for order resolution
        List<PersonaSectionDefinition> sectionDefs = personaSectionLookup.apply(agent.id());
        Map<String, PersonaSectionDefinition> defsByKey = new java.util.HashMap<>();
        if (sectionDefs != null) {
            for (PersonaSectionDefinition def : sectionDefs) {
                defsByKey.put(def.key(), def);
            }
        }

        // 2. For each entry in persona.sections(), create a PromptSection
        if (persona.sections() != null) {
            for (Map.Entry<String, List<String>> entry : persona.sections().entrySet()) {
                String key = entry.getKey();
                List<String> content = entry.getValue();
                int order = 500; // default order if no matching definition found
                PersonaSectionDefinition def = defsByKey.get(key);
                if (def != null) {
                    order = def.order();
                }
                sections.add(new PromptSection(key, content, order));
            }
        }

        // 3. If persona has additionalRules, add as a section
        if (persona.additionalRules() != null && !persona.additionalRules().isEmpty()) {
            sections.add(new PromptSection("additional-rules", persona.additionalRules(), 900));
        }

        // 4. If persona has importantInstructions, add as LAST section
        if (persona.importantInstructions() != null && !persona.importantInstructions().isEmpty()) {
            sections.add(new PromptSection("important-instructions", persona.importantInstructions(), 999));
        }

        // 5. Sort all sections by order
        sections.sort(Comparator.comparingInt(PromptSection::order));

        // AllowedTiers: intersect agent's tiers with persona's tiers
        List<TierId> agentTiers = agentTierLookup.apply(agent.id());
        List<TierId> allowedTiers = intersectTiers(agentTiers, persona.allowedTiers());

        // effectiveDisplayName
        String displayName = (persona.displayName() != null && !persona.displayName().isBlank())
                ? persona.displayName()
                : agent.name();

        // effectiveAvatar
        String avatar = (persona.avatar() != null && !persona.avatar().isBlank())
                ? persona.avatar()
                : "";

        // Build AgentProfileId
        AgentProfileId profileId = new AgentProfileId(agent.id(), persona.id());

        return new AgentProfile(profileId, agent, persona, displayName, avatar, sections, allowedTiers);
    }

    private List<TierId> intersectTiers(List<TierId> agentTiers, List<TierId> personaTiers) {
        if (agentTiers == null || agentTiers.isEmpty()) {
            return personaTiers != null ? personaTiers : List.of();
        }
        if (personaTiers == null || personaTiers.isEmpty()) {
            return agentTiers;
        }
        List<TierId> result = new ArrayList<>();
        for (TierId tier : agentTiers) {
            if (personaTiers.contains(tier)) {
                result.add(tier);
            }
        }
        return result;
    }
}
