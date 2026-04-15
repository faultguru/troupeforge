package com.troupeforge.tools.delegation;

import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolInstruction;
import com.troupeforge.core.tool.ToolParam;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ListAgentsTool implements Tool {

    public static final String NAME = "list_agents";

    private final AgentBucketRegistry bucketRegistry;

    public ListAgentsTool(AgentBucketRegistry bucketRegistry) {
        this.bucketRegistry = Objects.requireNonNull(bucketRegistry);
    }

    public record Request(
        @ToolParam(description = "Optional filter to narrow down agents by name or description", required = false)
        String filter
    ) {}

    public record AgentEntry(
        @ToolParam(description = "The persona ID to use when calling delegate_to_agent or handover_to_agent")
        String personaId,
        String displayName,
        String description
    ) {}

    public record Response(
        @ToolParam(description = "List of available agents. Always show the personaId to the user - it is the identifier they need to reference agents.")
        List<AgentEntry> agents,
        String instructions
    ) {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "List available agents. Returns personaId for each agent - use this personaId when calling delegate_to_agent or handover_to_agent.";
    }

    @Override
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    public Class<Response> responseType() {
        return Response.class;
    }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        AgentBucketId bucketId = context.requestContext().bucketId();

        AgentBucket bucket;
        try {
            bucket = bucketRegistry.getBucket(bucketId);
        } catch (IllegalStateException e) {
            return new Response(List.of(), null);
        }

        String filterLower = req.filter() != null ? req.filter().trim().toLowerCase() : null;

        // Collect entries with match priority for sorting
        record Scored(AgentEntry entry, int priority) {}
        List<Scored> scored = new ArrayList<>();

        for (Map.Entry<AgentProfileId, AgentProfile> entry : bucket.agentProfiles().entrySet()) {
            AgentProfile profile = entry.getValue();
            String personaId = profile.persona().id().value();
            String displayName = profile.effectiveDisplayName();
            String agentId = profile.agent().id().value();
            String agentDescription = profile.agent().description();

            if (filterLower == null) {
                scored.add(new Scored(new AgentEntry(personaId, displayName, agentDescription), 99));
                continue;
            }

            // Match priority: personaId > name/displayName > agentId > description
            // Lower number = higher priority (sorted ascending)
            int priority = matchPriority(filterLower, personaId, displayName, agentId, agentDescription);
            if (priority >= 0) {
                scored.add(new Scored(new AgentEntry(personaId, displayName, agentDescription), priority));
            }
        }

        // Sort by priority (best match first), stable sort preserves original order within same priority
        scored.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        List<AgentEntry> entries = scored.stream().map(Scored::entry).toList();

        return new Response(entries,
                "When presenting agents to the user, always lead with the personaId (e.g. 'bond', 'lord') as that is the identifier used to switch or delegate.");
    }

    @Override
    public ToolInstruction toolInstruction() {
        return new ToolInstruction(10, List.of(
                ToolInstruction.line("## Agent Discovery"),
                ToolInstruction.line("Use list_agents to discover available agents and their personaId values."),
                ToolInstruction.line("You can filter by name to narrow results. The personaId returned is the identifier you need when calling delegate_to_agent or handover_to_agent.")
        ));
    }

    /**
     * Returns match priority (lower = better), or -1 if no match.
     * Priority order: personaId exact (0) > personaId contains (1) >
     * displayName/name match (2) > agentId match (3) > description match (4)
     */
    private int matchPriority(String filter, String personaId, String displayName,
                                String agentId, String description) {
        // Exact personaId match
        if (personaId != null && personaId.toLowerCase().equals(filter)) {
            return 0;
        }
        // PersonaId contains
        if (personaId != null && personaId.toLowerCase().contains(filter)) {
            return 1;
        }
        // Display name / persona name contains
        if (displayName != null && displayName.toLowerCase().contains(filter)) {
            return 2;
        }
        // Agent ID contains
        if (agentId != null && agentId.toLowerCase().contains(filter)) {
            return 3;
        }
        // Description contains
        if (description != null && description.toLowerCase().contains(filter)) {
            return 4;
        }
        return -1;
    }
}
