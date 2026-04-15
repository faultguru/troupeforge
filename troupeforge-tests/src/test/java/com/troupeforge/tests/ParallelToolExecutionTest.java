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
@DisplayName("Parallel Tool Execution")
class ParallelToolExecutionTest {

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
                new RequestId("req-parallel-" + System.nanoTime()),
                requestor,
                TestBucketHelper.TEST_STAGE,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Multiple tool calls execute concurrently")
    void testMultipleToolsExecuteInParallel() {
        // Queue a response with 2 parallel tool calls
        ToolCall tc1 = new ToolCall("tc-1", "calculator", Map.of("operation", "add", "a", 1.0, "b", 2.0));
        ToolCall tc2 = new ToolCall("tc-2", "calculator", Map.of("operation", "multiply", "a", 3.0, "b", 4.0));

        LlmResponse multiToolResponse = new LlmResponse(
                null,
                FinishReason.TOOL_USE,
                new TokenUsage(10, 20, 30, 0, 0),
                List.of(tc1, tc2),
                "mock-model",
                Duration.ofMillis(1)
        );
        mockLlm.queueResponse(multiToolResponse);
        mockLlm.queueTextResponse("Results: 3 and 12");

        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.CALCULATOR_ALBERT);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Add 1+2 and multiply 3*4").join();

        assertNotNull(result);
        assertEquals("Results: 3 and 12", result.response());

        // Verify both tool executions were traced
        long toolExecCount = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.ToolExecution)
                .count();
        assertEquals(2, toolExecCount, "Should have 2 tool execution events for parallel calls");

        // Verify both succeeded
        boolean allSuccess = result.trace().events().stream()
                .filter(e -> e instanceof TraceEvent.ToolExecution)
                .map(e -> (TraceEvent.ToolExecution) e)
                .allMatch(TraceEvent.ToolExecution::success);
        assertTrue(allSuccess, "All parallel tool executions should succeed");
    }
}
