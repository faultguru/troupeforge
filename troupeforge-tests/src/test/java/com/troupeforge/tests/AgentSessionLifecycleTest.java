package com.troupeforge.tests;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.engine.session.AgentSessionFactoryImpl;
import com.troupeforge.infra.storage.InMemoryContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent Session Lifecycle")
class AgentSessionLifecycleTest {

    private AgentSessionFactoryImpl sessionFactory;
    private InMemoryContextStore contextStore;

    private static final AgentProfileId PROFILE_ID =
            new AgentProfileId(new AgentId("greeter"), new PersonaId("simon"));
    private static final OrganizationId ORG = new OrganizationId("test-org");
    private static final StageContext STAGE = StageContext.LIVE;

    @BeforeEach
    void setUp() {
        contextStore = new InMemoryContextStore();
        sessionFactory = new AgentSessionFactoryImpl(contextStore);
    }

    private RequestContext makeRequestContext() {
        return new RequestContext(
                RequestId.generate(),
                new RequestorContext(new UserId("test-user"), ORG),
                STAGE, Instant.now()
        );
    }

    @Test
    void testNewSessionCreatesContext() {
        RequestContext reqCtx = makeRequestContext();
        AgentContext session = sessionFactory.newSession(reqCtx, PROFILE_ID);

        assertNotNull(session);
        assertNotNull(session.sessionId());
        assertEquals(reqCtx.requestId(), session.requestId());
        assertEquals(PROFILE_ID, session.agentProfileId());
        assertNull(session.parentSessionId(), "New session should not have a parent");

        // Verify it was persisted to the store
        Optional<AgentContext> loaded = contextStore.load(session.sessionId());
        assertTrue(loaded.isPresent(), "Session should be saved to context store");
    }

    @Test
    void testDelegatedSessionHasParentSessionId() {
        RequestContext reqCtx = makeRequestContext();
        AgentContext parentSession = sessionFactory.newSession(reqCtx, PROFILE_ID);

        AgentProfileId childProfileId =
                new AgentProfileId(new AgentId("researcher"), new PersonaId("guru"));
        AgentContext childSession = sessionFactory.newDelegatedSession(
                reqCtx, childProfileId, parentSession.sessionId());

        assertNotNull(childSession.parentSessionId());
        assertEquals(parentSession.sessionId(), childSession.parentSessionId());
        assertNotEquals(parentSession.sessionId(), childSession.sessionId(),
                "Child should have a different session ID");
        assertEquals(reqCtx.requestId(), childSession.requestId(),
                "Child should share the same request ID");
    }

    @Test
    void testResumeSessionLoadsFromStore() {
        RequestContext reqCtx = makeRequestContext();
        AgentContext original = sessionFactory.newSession(reqCtx, PROFILE_ID);

        AgentContext resumed = sessionFactory.resumeSession(original.sessionId());

        assertEquals(original.sessionId(), resumed.sessionId());
        assertEquals(original.requestId(), resumed.requestId());
        assertEquals(original.agentProfileId(), resumed.agentProfileId());
    }

    @Test
    void testSessionContextPersistsAfterExecution() {
        RequestContext reqCtx = makeRequestContext();
        AgentContext session = sessionFactory.newSession(reqCtx, PROFILE_ID);

        // Simulate adding state and saving
        session.state().put("turnCount", 1);
        contextStore.save(session);

        // Reload and verify state persisted
        AgentContext reloaded = contextStore.load(session.sessionId()).orElseThrow();
        assertEquals(1, reloaded.state().get("turnCount"));
    }
}
