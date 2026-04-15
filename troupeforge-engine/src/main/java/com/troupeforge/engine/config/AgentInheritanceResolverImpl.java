package com.troupeforge.engine.config;

import com.troupeforge.core.agent.AgentDefinition;
import com.troupeforge.core.agent.AgentType;
import com.troupeforge.core.agent.DirectReturnPolicy;
import com.troupeforge.core.agent.InheritablePromptSections;
import com.troupeforge.core.agent.InheritableSet;
import com.troupeforge.core.agent.InheritanceAction;
import com.troupeforge.core.agent.PersonaSectionDefinition;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.CapabilityId;
import com.troupeforge.core.id.ContractCapabilityId;
import com.troupeforge.core.id.GuardrailId;
import com.troupeforge.core.id.ToolId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AgentInheritanceResolverImpl implements AgentInheritanceResolver {

    @Override
    public List<ResolvedAgent> resolve(List<AgentDefinition> definitions) {
        Map<AgentId, AgentDefinition> byId = definitions.stream()
                .collect(Collectors.toMap(AgentDefinition::id, d -> d));

        List<ResolvedAgent> resolved = new ArrayList<>();
        for (AgentDefinition def : definitions) {
            resolved.add(resolveAgent(def, byId));
        }
        return Collections.unmodifiableList(resolved);
    }

    private ResolvedAgent resolveAgent(AgentDefinition agent, Map<AgentId, AgentDefinition> byId) {
        List<AgentDefinition> chain = buildAncestorChain(agent, byId);
        List<AgentId> ancestorChain = chain.stream()
                .map(AgentDefinition::id)
                .collect(Collectors.toList());

        Set<CapabilityId> capabilities = resolveSet(chain, AgentDefinition::capabilities);
        Set<GuardrailId> guardrails = resolveSet(chain, AgentDefinition::guardrails);
        Set<ToolId> tools = resolveSet(chain, AgentDefinition::tools);
        Set<ContractCapabilityId> contractCapabilities = resolveSet(chain, AgentDefinition::contractCapabilities);
        List<PromptSection> promptSections = resolvePromptSections(chain);
        DirectReturnPolicy directReturnPolicy = agent.directReturnPolicy();

        return new ResolvedAgent(
                agent.id(),
                agent.name(),
                agent.description(),
                agent.type(),
                agent.parent(),
                Collections.unmodifiableList(ancestorChain),
                Collections.unmodifiableSet(capabilities),
                Collections.unmodifiableSet(guardrails),
                Collections.unmodifiableSet(tools),
                Collections.unmodifiableSet(contractCapabilities),
                Collections.unmodifiableList(promptSections),
                directReturnPolicy,
                agent.maxConcurrency()
        );
    }

    /**
     * Build the ancestor chain from root to this agent (inclusive).
     * Root agent is one where parent == self.
     */
    private List<AgentDefinition> buildAncestorChain(AgentDefinition agent,
                                                      Map<AgentId, AgentDefinition> byId) {
        List<AgentDefinition> chain = new ArrayList<>();
        AgentDefinition current = agent;
        Set<AgentId> visited = new LinkedHashSet<>();

        while (current != null) {
            if (visited.contains(current.id())) {
                break;
            }
            visited.add(current.id());
            chain.add(0, current); // prepend so root is first

            if (isRoot(current)) {
                break;
            }
            current = current.parent() != null ? byId.get(current.parent()) : null;
        }
        return chain;
    }

    private boolean isRoot(AgentDefinition agent) {
        return agent.parent() != null && agent.parent().equals(agent.id());
    }

    /**
     * Resolve an InheritableSet field across the ancestor chain.
     * Chain is ordered root-first, so we accumulate from root down.
     */
    private <T> Set<T> resolveSet(List<AgentDefinition> chain,
                                   java.util.function.Function<AgentDefinition, InheritableSet<T>> extractor) {
        Set<T> accumulated = new LinkedHashSet<>();

        for (AgentDefinition def : chain) {
            InheritableSet<T> set = extractor.apply(def);
            if (set == null) {
                // null means inherit everything from parent, no changes
                continue;
            }

            InheritanceAction action = set.action() != null ? set.action() : InheritanceAction.INHERIT;
            List<T> values = set.values() != null ? set.values() : Collections.emptyList();

            switch (action) {
                case INHERIT:
                    accumulated.addAll(values);
                    break;
                case REPLACE:
                    accumulated.clear();
                    accumulated.addAll(values);
                    break;
                case REMOVE:
                    accumulated.removeAll(values);
                    break;
            }
        }
        return accumulated;
    }

    /**
     * Resolve prompt sections across the ancestor chain.
     * Child sections with the same key override parent sections.
     * Final list is sorted by order.
     */
    private List<PromptSection> resolvePromptSections(List<AgentDefinition> chain) {
        Map<String, PromptSection> byKey = new LinkedHashMap<>();

        for (AgentDefinition def : chain) {
            InheritablePromptSections ps = def.promptSections();
            if (ps == null) {
                continue;
            }

            InheritanceAction action = ps.action() != null ? ps.action() : InheritanceAction.INHERIT;
            List<PromptSection> sections = ps.sections() != null ? ps.sections() : Collections.emptyList();

            switch (action) {
                case INHERIT:
                    for (PromptSection section : sections) {
                        byKey.put(section.key(), section);
                    }
                    break;
                case REPLACE:
                    byKey.clear();
                    for (PromptSection section : sections) {
                        byKey.put(section.key(), section);
                    }
                    break;
                case REMOVE:
                    for (PromptSection section : sections) {
                        byKey.remove(section.key());
                    }
                    break;
            }
        }

        List<PromptSection> result = new ArrayList<>(byKey.values());
        result.sort(Comparator.comparingInt(PromptSection::order));
        return result;
    }
}
