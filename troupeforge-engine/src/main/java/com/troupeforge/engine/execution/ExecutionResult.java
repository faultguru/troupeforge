package com.troupeforge.engine.execution;

import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.llm.TokenUsage;

import java.util.List;

/**
 * Result of executing an agent.
 * When a handover occurs, {@code handoverSessionId} carries the child session ID
 * so the caller can resume with the correct agent on the next turn.
 */
public record ExecutionResult(
    String response,
    AgentProfileId respondingAgent,
    ExecutionTrace trace,
    TokenUsage totalUsage,
    List<InferenceSummary> inferences,
    AgentSessionId handoverSessionId
) {
    /** Convenience constructor for non-handover results. */
    public ExecutionResult(String response, AgentProfileId respondingAgent,
                            ExecutionTrace trace, TokenUsage totalUsage,
                            List<InferenceSummary> inferences) {
        this(response, respondingAgent, trace, totalUsage, inferences, null);
    }

    /** Legacy constructor without inferences. */
    public ExecutionResult(String response, AgentProfileId respondingAgent,
                            ExecutionTrace trace, TokenUsage totalUsage) {
        this(response, respondingAgent, trace, totalUsage, List.of(), null);
    }
}
