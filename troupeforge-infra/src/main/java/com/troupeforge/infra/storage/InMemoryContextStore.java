package com.troupeforge.infra.storage;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.storage.QueryCriteria;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryContextStore implements ContextStore {

    private final ConcurrentHashMap<String, AgentContext> store = new ConcurrentHashMap<>();

    @Override
    public void save(AgentContext context) {
        store.put(context.sessionId().value(), context);
    }

    @Override
    public Optional<AgentContext> load(AgentSessionId sessionId) {
        return Optional.ofNullable(store.get(sessionId.value()));
    }

    @Override
    public void delete(AgentSessionId sessionId) {
        store.remove(sessionId.value());
    }

    @Override
    public List<AgentContext> findByRequest(RequestId requestId) {
        return store.values().stream()
                .filter(ctx -> Objects.equals(ctx.requestId(), requestId))
                .toList();
    }

    @Override
    public List<AgentContext> findByBucket(AgentBucketId bucketId, QueryCriteria criteria) {
        return store.values().stream()
                .filter(ctx -> Objects.equals(ctx.bucketId(), bucketId))
                .limit(criteria.limit() > 0 ? criteria.limit() : Long.MAX_VALUE)
                .toList();
    }

    @Override
    public List<AgentContext> findByParentSession(AgentSessionId parentSessionId) {
        return store.values().stream()
                .filter(ctx -> Objects.equals(ctx.parentSessionId(), parentSessionId))
                .toList();
    }
}
