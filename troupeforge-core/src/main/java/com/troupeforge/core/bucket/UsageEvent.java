package com.troupeforge.core.bucket;

import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.llm.CostEstimate;
import com.troupeforge.core.llm.TokenUsage;

import java.time.Instant;

public record UsageEvent(
    AgentBucketId bucketId,
    AgentProfileId agentProfileId,
    AgentSessionId sessionId,
    RequestId requestId,
    String model,
    TokenUsage tokenUsage,
    CostEstimate cost,
    Instant timestamp
) {}
