package com.troupeforge.tests;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.tests.support.MockLlmProvider;
import com.troupeforge.tests.support.TestBucketHelper;
import com.troupeforge.tests.support.TestSpringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Handover Flow")
class HandoverFlowTest {

    @Autowired
    private AgentBucketRegistry bucketRegistry;

    @Autowired
    private MockLlmProvider mockLlm;

    @Autowired
    private TroupeForgeEntryPoint entryPoint;

    @Autowired
    private ContextStore contextStore;

    private final RequestorContext requestor = new RequestorContext(
            new UserId("test-user"), TestBucketHelper.TEST_ORG);

    @BeforeEach
    void setUp() {
        mockLlm.reset();
        TestBucketHelper.loadTestBucket(bucketRegistry);
    }

    @Test
    void testHandoverReturnsChildResultDirectly() {
        // 1. Parent (dispatcher:linda) calls handover_to_agent to greeter:simon
        mockLlm.queueToolCallResponse("tc-1", "handover_to_agent", Map.of(
                "personaId", "simon",
                "message", "Greet the user warmly"
        ));
        // 2. Child (greeter:simon) responds with text -- this is the final result
        mockLlm.queueTextResponse("Simon says: Welcome!");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Greet me", null
        ).join();

        // The response should be the child's direct output, not a parent synthesis
        assertEquals("Simon says: Welcome!", response.response());
    }

    @Test
    void testHandoverCreatesChildSession() {
        mockLlm.queueToolCallResponse("tc-1", "handover_to_agent", Map.of(
                "agentId", "greeter",
                "personaId", "bond",
                "message", "Greet them in style"
        ));
        mockLlm.queueTextResponse("Bond. James Bond. Welcome.");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Greet me", null
        ).join();

        // Should have created sessions for both parent and child
        List<AgentContext> sessions = contextStore.findByRequest(response.requestId());
        assertTrue(sessions.size() >= 2,
                "Handover should create sessions for both parent and child");

        // At least one session should have a parentSessionId set
        boolean hasChildSession = sessions.stream()
                .anyMatch(ctx -> ctx.parentSessionId() != null);
        assertTrue(hasChildSession, "Child session should have parentSessionId set");
    }

    @Test
    @DisplayName("Handover returns child session ID so next turn resumes with new agent")
    void testHandoverSessionIdPointsToChildAgent() {
        // Linda hands over to Bond
        mockLlm.queueToolCallResponse("tc-1", "handover_to_agent", Map.of(
                "personaId", "bond",
                "message", "Greet them in style"
        ));
        mockLlm.queueTextResponse("Bond. James Bond.");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Hello", null
        ).join();

        // The responding agent should be Bond (the handover target)
        assertEquals(TestBucketHelper.GREETER_BOND, response.respondingAgent(),
                "Handover should report Bond as the responding agent");

        // The session ID in the response should be Bond's session, not Linda's
        // Verify by loading the session - it should point to Bond's agent profile
        var resumedContext = contextStore.load(response.sessionId());
        assertTrue(resumedContext.isPresent(), "Session should be loadable");
        assertEquals(TestBucketHelper.GREETER_BOND, resumedContext.get().agentProfileId(),
                "Resumed session should be Bond's, not Linda's");
    }

    @Test
    void testHandoverSkipsParentResponseInference() {
        // In a handover, the parent should NOT make another LLM call after the handover.
        // Total calls: 1 to parent (returning handover tool call) + 1 to child (returning text)
        mockLlm.queueToolCallResponse("tc-1", "handover_to_agent", Map.of(
                "personaId", "simon",
                "message", "Hello"
        ));
        mockLlm.queueTextResponse("Simon says: Hello");

        entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Greet me", null
        ).join();

        // Exactly 2 LLM calls: one for parent (handover tool call), one for child (text)
        assertEquals(2, mockLlm.getRequestHistory().size(),
                "Handover should result in exactly 2 LLM calls: parent handover + child response");
    }
}
