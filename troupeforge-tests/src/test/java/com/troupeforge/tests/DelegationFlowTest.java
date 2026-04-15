package com.troupeforge.tests;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.llm.LlmRequest;
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
@DisplayName("Delegation Flow")
class DelegationFlowTest {

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
    void testDelegateToAgentCreatesChildSession() {
        // 1. Parent (dispatcher:linda) calls delegate_to_agent to greeter:simon
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "Say hello"
        ));
        // 2. Child (greeter:simon) responds with text
        mockLlm.queueTextResponse("Simon says: hello!");
        // 3. Parent receives the delegation result and produces final response
        mockLlm.queueTextResponse("The greeter said: Simon says: hello!");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Greet me", null
        ).join();

        assertNotNull(response);
        assertEquals("The greeter said: Simon says: hello!", response.response());

        // Verify that at least two sessions were created (parent + child)
        List<AgentContext> sessions = contextStore.findByRequest(response.requestId());
        assertTrue(sessions.size() >= 2,
                "Delegation should create at least 2 sessions (parent + child)");
    }

    @Test
    void testDelegateResultReturnedAsToolResult() {
        // Delegation flow: parent delegates, child responds, parent uses child's response
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "Hello world"
        ));
        mockLlm.queueTextResponse("Simon says: Hello world");
        mockLlm.queueTextResponse("Delegation result received.");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Please greet me", null
        ).join();

        // 3 LLM calls: parent -> delegate tool, child -> text, parent -> final
        assertEquals(3, mockLlm.getRequestHistory().size());

        // The final parent response should be present
        assertEquals("Delegation result received.", response.response());
    }

    @Test
    void testDelegationPreservesRequestId() {
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "Test"
        ));
        mockLlm.queueTextResponse("Child response");
        mockLlm.queueTextResponse("Parent final");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Test delegation", null
        ).join();

        RequestId requestId = response.requestId();
        List<AgentContext> sessions = contextStore.findByRequest(requestId);

        // All sessions should share the same requestId
        for (AgentContext ctx : sessions) {
            assertEquals(requestId, ctx.requestId(),
                    "All sessions in a delegation chain should share the same requestId");
        }
    }

    @Test
    @DisplayName("Bond delegates to Lord and stays as responding agent")
    void testBondDelegatesToLordAndStaysAsBond() {
        // Bond calls delegate_to_agent to Lord
        mockLlm.queueToolCallResponse("tc-bond-1", "delegate_to_agent", Map.of(
                "personaId", "lord",
                "message", "How are you, Lord?"
        ));
        // Lord responds in his formal style
        mockLlm.queueTextResponse("I am quite well, dear fellow. Splendid weather for tea, I dare say.");
        // Bond relays Lord's response in his own suave style
        mockLlm.queueTextResponse("I checked with the old chap. He's doing splendidly — something about tea, naturally.");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.GREETER_BOND,
                "Ask Lord how he is", null
        ).join();

        assertNotNull(response);
        // The responding agent should be Bond (delegation, not handover)
        assertEquals(TestBucketHelper.GREETER_BOND, response.respondingAgent(),
                "Delegation should keep Bond as the responding agent, not switch to Lord");
        assertTrue(response.response().contains("splendidly"),
                "Bond should relay Lord's response");

        // 3 LLM calls: Bond -> delegate tool, Lord -> text, Bond -> final
        assertEquals(3, mockLlm.getRequestHistory().size());
    }

    @Test
    void testDelegationCreatesNewSessionId() {
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "Test"
        ));
        mockLlm.queueTextResponse("Child response");
        mockLlm.queueTextResponse("Parent final");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Test delegation", null
        ).join();

        List<AgentContext> sessions = contextStore.findByRequest(response.requestId());
        assertTrue(sessions.size() >= 2);

        // All session IDs should be unique
        long distinctSessions = sessions.stream()
                .map(AgentContext::sessionId)
                .distinct()
                .count();
        assertEquals(sessions.size(), distinctSessions,
                "Each delegation should produce a unique session ID");
    }
}
