package com.troupeforge.core.context;

import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.llm.LlmMessage;
import com.troupeforge.core.storage.Storable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentContext(
    AgentSessionId sessionId,
    RequestId requestId,
    AgentProfileId agentProfileId,
    AgentBucketId bucketId,
    AgentSessionId parentSessionId,    // null for root session, set for delegated sessions
    Instant startedAt,
    Map<String, Object> state,
    List<LlmMessage> conversationHistory   // mutable list — grows across turns
) implements Storable {
    public String id() {
        return sessionId.value();
    }

    public long version() {
        return 0;
    }
}
