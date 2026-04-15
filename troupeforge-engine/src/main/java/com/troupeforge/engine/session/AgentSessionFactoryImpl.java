package com.troupeforge.engine.session;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.storage.ContextStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class AgentSessionFactoryImpl implements AgentSessionFactory {

    private final ContextStore contextStore;

    public AgentSessionFactoryImpl(ContextStore contextStore) {
        this.contextStore = Objects.requireNonNull(contextStore, "contextStore must not be null");
    }

    @Override
    public AgentContext newSession(RequestContext request, AgentProfileId profileId) {
        var context = new AgentContext(
            AgentSessionId.generate(),
            request.requestId(),
            profileId,
            request.bucketId(),
            null,
            Instant.now(),
            new HashMap<>(),
            new ArrayList<>()
        );
        contextStore.save(context);
        return context;
    }

    @Override
    public AgentContext newDelegatedSession(RequestContext request, AgentProfileId profileId,
                                           AgentSessionId parentSessionId) {
        Objects.requireNonNull(parentSessionId, "parentSessionId must not be null for delegated sessions");
        var context = new AgentContext(
            AgentSessionId.generate(),
            request.requestId(),
            profileId,
            request.bucketId(),
            parentSessionId,
            Instant.now(),
            new HashMap<>(),
            new ArrayList<>()
        );
        contextStore.save(context);
        return context;
    }

    @Override
    public AgentContext resumeSession(AgentSessionId sessionId) {
        return contextStore.load(sessionId)
            .orElseThrow(() -> new IllegalStateException(
                "No session found for id: " + sessionId.value()));
    }
}
