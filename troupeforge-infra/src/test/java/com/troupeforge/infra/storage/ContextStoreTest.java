package com.troupeforge.infra.storage;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.infra.storage.InMemoryContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Context Store Persistence")
class ContextStoreTest {

    private InMemoryContextStore store;

    private static final AgentProfileId PROFILE =
            new AgentProfileId(new AgentId("greeter"), new PersonaId("simon"));
    private static final AgentBucketId BUCKET =
            new AgentBucketId("test-org:live");

    @BeforeEach
    void setUp() {
        store = new InMemoryContextStore();
    }

    private AgentContext makeContext(AgentSessionId sessionId, RequestId requestId,
                                     AgentSessionId parentSessionId) {
        return new AgentContext(
                sessionId, requestId, PROFILE, BUCKET,
                parentSessionId, Instant.now(), new HashMap<>(), new java.util.ArrayList<>()
        );
    }

    @Test
    void testSaveAndLoadAgentContext() {
        AgentSessionId sessionId = AgentSessionId.generate();
        RequestId requestId = RequestId.generate();
        AgentContext context = makeContext(sessionId, requestId, null);

        store.save(context);

        Optional<AgentContext> loaded = store.load(sessionId);
        assertTrue(loaded.isPresent());
        assertEquals(sessionId, loaded.get().sessionId());
        assertEquals(requestId, loaded.get().requestId());
        assertEquals(PROFILE, loaded.get().agentProfileId());
        assertEquals(BUCKET, loaded.get().bucketId());
    }

    @Test
    void testFindByRequestReturnsAllSessions() {
        RequestId requestId = RequestId.generate();
        AgentSessionId session1 = AgentSessionId.generate();
        AgentSessionId session2 = AgentSessionId.generate();
        AgentSessionId session3 = AgentSessionId.generate();

        store.save(makeContext(session1, requestId, null));
        store.save(makeContext(session2, requestId, session1));
        store.save(makeContext(session3, RequestId.generate(), null)); // different request

        List<AgentContext> results = store.findByRequest(requestId);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(c -> c.sessionId().equals(session1)));
        assertTrue(results.stream().anyMatch(c -> c.sessionId().equals(session2)));
    }

    @Test
    void testFindByParentSession() {
        RequestId requestId = RequestId.generate();
        AgentSessionId parentId = AgentSessionId.generate();
        AgentSessionId child1 = AgentSessionId.generate();
        AgentSessionId child2 = AgentSessionId.generate();
        AgentSessionId unrelated = AgentSessionId.generate();

        store.save(makeContext(parentId, requestId, null));
        store.save(makeContext(child1, requestId, parentId));
        store.save(makeContext(child2, requestId, parentId));
        store.save(makeContext(unrelated, requestId, null));

        List<AgentContext> children = store.findByParentSession(parentId);
        assertEquals(2, children.size());
        assertTrue(children.stream().allMatch(c -> parentId.equals(c.parentSessionId())));
    }

    @Test
    void testDeleteContext() {
        AgentSessionId sessionId = AgentSessionId.generate();
        AgentContext context = makeContext(sessionId, RequestId.generate(), null);

        store.save(context);
        assertTrue(store.load(sessionId).isPresent());

        store.delete(sessionId);
        assertFalse(store.load(sessionId).isPresent());
    }
}
