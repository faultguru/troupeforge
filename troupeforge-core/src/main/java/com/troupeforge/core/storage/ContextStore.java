package com.troupeforge.core.storage;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;

import java.util.List;
import java.util.Optional;

public interface ContextStore {
    void save(AgentContext context);
    Optional<AgentContext> load(AgentSessionId sessionId);
    void delete(AgentSessionId sessionId);
    List<AgentContext> findByRequest(RequestId requestId);
    List<AgentContext> findByBucket(AgentBucketId bucketId, QueryCriteria criteria);
    List<AgentContext> findByParentSession(AgentSessionId parentSessionId);
}
