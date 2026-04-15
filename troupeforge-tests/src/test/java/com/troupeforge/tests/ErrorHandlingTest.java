package com.troupeforge.tests;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.llm.FinishReason;
import com.troupeforge.core.llm.LlmResponse;
import com.troupeforge.core.llm.TokenUsage;
import com.troupeforge.core.llm.ToolCall;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Error Handling")
class ErrorHandlingTest {

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
                new RequestId("req-err-" + System.nanoTime()),
                requestor,
                TestBucketHelper.TEST_STAGE,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Unknown tool returns error message")
    void testUnknownToolReturnsError() {
        // LLM calls a tool that doesn't exist
        mockLlm.queueToolCallResponse("tc-1", "nonexistent_tool", Map.of("arg", "value"));
        mockLlm.queueTextResponse("Handled the error");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.GREETER_SIMON);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Do something").join();

        assertNotNull(result);
        // The executor should have continued after the unknown tool error
        assertEquals("Handled the error", result.response());

        // Check trace for guardrail-blocked or failed tool event
        long errorCount = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.Error ||
                        (e instanceof TraceEvent.ToolExecution te && !te.success()))
                .count();
        assertTrue(errorCount >= 1, "Should have at least 1 error or failed tool execution event");
    }

    @Test
    @DisplayName("Tool exception returns error message to LLM")
    void testToolExceptionReturnsError() {
        // Write file tool with path traversal should fail
        mockLlm.queueToolCallResponse("tc-1", "write_file", Map.of(
                "path", "../../escape.txt", "content", "malicious"));
        mockLlm.queueTextResponse("Write failed as expected");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.GREETER_SIMON);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Write file").join();

        assertNotNull(result);
        assertEquals("Write failed as expected", result.response());
    }

    @Test
    @DisplayName("Max iterations reached returns appropriate message")
    void testMaxIterationsReached() {
        // Queue 11 tool call responses (MAX_LOOP_ITERATIONS is 10) to exhaust the loop.
        // Each iteration will call think and then loop again.
        for (int i = 0; i < 11; i++) {
            mockLlm.queueToolCallResponse("tc-" + i, "think", Map.of("thought", "iteration " + i));
        }

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.CALCULATOR_ALBERT);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Loop forever").join();

        assertNotNull(result);
        assertTrue(result.response().contains("Max loop iterations"),
                "Should indicate max iterations reached: " + result.response());
    }
}
