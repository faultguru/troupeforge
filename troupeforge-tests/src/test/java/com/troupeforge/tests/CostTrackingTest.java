package com.troupeforge.tests;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.llm.TokenUsage;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.execution.AgentExecutor;
import com.troupeforge.engine.execution.CostAccumulator;
import com.troupeforge.engine.execution.ExecutionResult;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Cost Tracking")
class CostTrackingTest {

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

    @BeforeEach
    void setUp() {
        mockLlm.reset();
        TestBucketHelper.loadTestBucket(bucketRegistry);
    }

    @Test
    @DisplayName("CostAccumulator sums token usages correctly")
    void testCostAccumulatorSumsTokens() {
        CostAccumulator accumulator = new CostAccumulator();

        accumulator.add(new TokenUsage(10, 20, 30, 5, 2));
        accumulator.add(new TokenUsage(15, 25, 40, 3, 1));

        TokenUsage total = accumulator.total();
        assertEquals(25, total.inputTokens(), "Input tokens should sum");
        assertEquals(45, total.outputTokens(), "Output tokens should sum");
        assertEquals(70, total.totalTokens(), "Total tokens should be input + output");
        assertEquals(8, total.cacheReadTokens(), "Cache read tokens should sum");
        assertEquals(3, total.cacheCreationTokens(), "Cache creation tokens should sum");
    }

    @Test
    @DisplayName("ExecutionResult includes total token usage")
    void testResponseIncludesTotalUsage() {
        mockLlm.queueTextResponse("Hello");

        RequestContext requestContext = new RequestContext(
                new RequestId("req-cost-" + System.nanoTime()),
                requestor,
                TestBucketHelper.TEST_STAGE,
                Instant.now()
        );
        AgentContext ctx = sessionFactory.newSession(
                requestContext, TestBucketHelper.GREETER_SIMON);
        ExecutionResult result = agentExecutor.execute(requestContext, ctx.sessionId(), "Hi").join();

        assertNotNull(result.totalUsage(), "ExecutionResult should include totalUsage");
        assertTrue(result.totalUsage().inputTokens() > 0, "Should have input tokens");
        assertTrue(result.totalUsage().outputTokens() > 0, "Should have output tokens");
    }
}
