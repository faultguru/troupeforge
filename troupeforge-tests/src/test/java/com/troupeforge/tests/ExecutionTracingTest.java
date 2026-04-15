package com.troupeforge.tests;

import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.llm.LlmRequest;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.execution.AgentExecutor;
import com.troupeforge.engine.execution.ExecutionResult;
import com.troupeforge.engine.execution.TraceEvent;
import com.troupeforge.engine.session.AgentSessionFactory;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.context.AgentContext;
import com.troupeforge.tests.support.MockLlmProvider;
import com.troupeforge.tests.support.TestBucketHelper;
import com.troupeforge.tests.support.TestSpringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Execution Tracing")
class ExecutionTracingTest {

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
                new RequestId("req-trace-" + System.nanoTime()),
                requestor,
                TestBucketHelper.TEST_STAGE,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Trace contains LLM call event for simple text response")
    void testTraceContainsLlmCallEvent() {
        mockLlm.queueTextResponse("Simple response");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.GREETER_SIMON);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Hello").join();

        assertNotNull(result.trace(), "Trace should not be null");
        assertFalse(result.trace().events().isEmpty(), "Trace should have events");

        long llmCallCount = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.LlmCall)
                .count();
        assertEquals(1, llmCallCount, "Should have exactly 1 LLM call event");
    }

    @Test
    @DisplayName("Trace contains tool execution event after tool call")
    void testTraceContainsToolExecutionEvent() {
        // Calculator agent calls calculator tool, then responds
        mockLlm.queueToolCallResponse("tc-1", "calculator", Map.of(
                "operation", "add", "a", 2.0, "b", 3.0));
        mockLlm.queueTextResponse("The answer is 5");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.CALCULATOR_ALBERT);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "What is 2+3?").join();

        assertNotNull(result.trace());

        long toolEventCount = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.ToolExecution)
                .count();
        assertTrue(toolEventCount >= 1, "Should have at least 1 tool execution event");

        TraceEvent.ToolExecution toolEvent = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.ToolExecution)
                .map(e -> (TraceEvent.ToolExecution) e)
                .findFirst()
                .orElseThrow();
        assertEquals("calculator", toolEvent.toolName());
        assertTrue(toolEvent.success());
    }

    @Test
    @DisplayName("Trace captures token usage accumulation")
    void testTraceCapturesTokenUsage() {
        // Two LLM calls (tool call + final response)
        mockLlm.queueToolCallResponse("tc-1", "think", Map.of("thought", "thinking..."));
        mockLlm.queueTextResponse("Done thinking");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.CALCULATOR_ALBERT);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Think and respond").join();

        assertNotNull(result.totalUsage(), "Total usage should not be null");
        // MockLlmProvider returns 10 input, 20 output per call => 2 calls = 20 input, 40 output
        assertEquals(20, result.totalUsage().inputTokens(), "Input tokens should be accumulated");
        assertEquals(40, result.totalUsage().outputTokens(), "Output tokens should be accumulated");
    }

    @Test
    @DisplayName("Trace contains delegation event")
    void testTraceContainsDelegationEvent() {
        // dispatcher:linda delegates to simon
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon", "message", "Hello"));
        mockLlm.queueTextResponse("Simon says: Hello");
        mockLlm.queueTextResponse("Done");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.DISPATCHER_LINDA);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Greet me").join();

        assertNotNull(result.trace());

        long delegationCount = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.Delegation)
                .count();
        assertTrue(delegationCount >= 1, "Should have at least 1 delegation event");

        TraceEvent.Delegation delegation = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.Delegation)
                .map(e -> (TraceEvent.Delegation) e)
                .findFirst()
                .orElseThrow();
        assertEquals("simon", delegation.targetPersona());
        assertTrue(delegation.success());
    }
}
