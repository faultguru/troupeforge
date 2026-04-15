package com.troupeforge.tests;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.id.*;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.execution.AgentExecutor;
import com.troupeforge.engine.execution.ExecutionResult;
import com.troupeforge.engine.execution.TraceEvent;
import com.troupeforge.engine.session.AgentSessionFactory;
import com.troupeforge.tests.support.MockLlmProvider;
import com.troupeforge.tests.support.TestBucketHelper;
import com.troupeforge.tests.support.TestSpringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Guardrail Enforcement")
class GuardrailTest {

    @Autowired
    private AgentBucketRegistry bucketRegistry;

    @Autowired
    private MockLlmProvider mockLlm;

    @Autowired
    private AgentExecutor agentExecutor;

    @Autowired
    private AgentSessionFactory sessionFactory;

    private final RequestorContext requestor = new RequestorContext(
            new UserId("test-user"), TestBucketHelper.TEST_ORG);

    private RequestContext requestContext;

    @BeforeEach
    void setUp() {
        mockLlm.reset();
        TestBucketHelper.loadTestBucket(bucketRegistry);
        requestContext = new RequestContext(
                new RequestId("req-guard-" + System.nanoTime()),
                requestor,
                TestBucketHelper.TEST_STAGE,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Agent with restricted tools cannot call disallowed tool")
    void testGuardrailBlocksDisallowedTool() {
        // GREETER_SIMON can only use delegate_to_agent and list_agents
        // Try to call calculator tool - should be blocked by guardrail
        mockLlm.queueToolCallResponse("tc-1", "calculator", Map.of(
                "operation", "add", "a", 1.0, "b", 2.0));
        mockLlm.queueTextResponse("OK, calculator was blocked");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.GREETER_SIMON);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Calculate 1+2").join();

        assertNotNull(result);
        // The response should indicate the executor continued after guardrail block
        assertEquals("OK, calculator was blocked", result.response());

        // Trace should have an Error event for the guardrail block
        long guardrailErrors = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.Error err && err.message().contains("Guardrail"))
                .count();
        assertTrue(guardrailErrors >= 1, "Should have at least 1 guardrail error in trace");
    }

    @Test
    @DisplayName("Agent with allowed tools can execute those tools")
    void testAllowedToolSucceeds() {
        // CALCULATOR_ALBERT has calculator, think, memory
        mockLlm.queueToolCallResponse("tc-1", "calculator", Map.of(
                "operation", "multiply", "a", 6.0, "b", 7.0));
        mockLlm.queueTextResponse("The answer is 42");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.CALCULATOR_ALBERT);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "What is 6*7?").join();

        assertNotNull(result);
        assertEquals("The answer is 42", result.response());

        // Trace should have a successful ToolExecution event
        long successToolCount = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.ToolExecution te && te.success())
                .count();
        assertTrue(successToolCount >= 1, "Should have successful tool execution");
    }
}
