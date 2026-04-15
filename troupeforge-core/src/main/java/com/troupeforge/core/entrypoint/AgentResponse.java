package com.troupeforge.core.entrypoint;

import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.llm.TokenUsage;

import java.time.Instant;
import java.util.List;

public record AgentResponse(
    RequestId requestId,
    AgentSessionId sessionId,
    AgentProfileId respondingAgent,
    String response,
    Instant completedAt,
    TokenUsage usage,
    List<InferenceSummaryDto> inferences,
    long latencyMs
) {
    /**
     * Per-inference breakdown showing which persona, model, and tokens were used.
     */
    public record InferenceSummaryDto(
        String personaId,
        String model,
        long latencyMs,
        int inputTokens,
        int outputTokens,
        int totalTokens
    ) {}
}
