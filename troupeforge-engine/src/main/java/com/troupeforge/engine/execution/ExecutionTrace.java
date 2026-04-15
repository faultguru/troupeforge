package com.troupeforge.engine.execution;

import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.llm.TokenUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ExecutionTrace(
    RequestId requestId,
    AgentSessionId sessionId,
    AgentProfileId agentProfileId,
    List<TraceEvent> events,
    Instant startedAt,
    Instant completedAt,
    TokenUsage totalUsage,
    Duration totalDuration
) {}
