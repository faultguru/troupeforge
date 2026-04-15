package com.troupeforge.tests;

import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.llm.LlmMessage;
import com.troupeforge.core.llm.LlmRequest;
import com.troupeforge.core.llm.MessageContent;
import com.troupeforge.core.llm.MessageRole;
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
@DisplayName("Context Carryover")
class ContextCarryoverTest {

    @Autowired
    private AgentBucketRegistry bucketRegistry;

    @Autowired
    private MockLlmProvider mockLlm;

    @Autowired
    private TroupeForgeEntryPoint entryPoint;

    private final RequestorContext requestor = new RequestorContext(
            new UserId("test-user"), TestBucketHelper.TEST_ORG);

    @BeforeEach
    void setUp() {
        mockLlm.reset();
        TestBucketHelper.loadTestBucket(bucketRegistry);
    }

    @Test
    void testChildAgentGetsNoParentContext() {
        // Parent (dispatcher:linda) delegates to child (greeter:simon)
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "Say hello"
        ));
        // Child responds
        mockLlm.queueTextResponse("Hello from Simon!");
        // Parent produces final response
        mockLlm.queueTextResponse("Done.");

        entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Please greet me", null
        ).join();

        // Request 0: parent's first call (SYSTEM + prefetch(ASSISTANT+TOOL) + USER)
        // Request 1: child's call (SYSTEM + USER)  — should have NO parent conversation
        // Request 2: parent's second call (SYSTEM + prefetch + USER + ASSISTANT[tool_use] + TOOL[result])
        List<LlmRequest> history = mockLlm.getRequestHistory();
        assertEquals(3, history.size());

        LlmRequest childRequest = history.get(1);
        List<LlmMessage> childMessages = childRequest.messages();

        // Child should have exactly SYSTEM + USER (no inherited parent context)
        assertEquals(2, childMessages.size(),
                "Child agent should receive only SYSTEM and USER messages, no parent context");
        assertEquals(MessageRole.SYSTEM, childMessages.get(0).role());
        assertEquals(MessageRole.USER, childMessages.get(1).role());

        // The user message to the child is the delegation message, not the parent's original
        String childUserMessage = extractText(childMessages.get(1));
        assertEquals("Say hello", childUserMessage,
                "Child should receive the delegation message, not the parent's original message");
    }

    @Test
    void testParentPicksUpContextAfterChildResponds() {
        // Parent delegates to child
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "Say hello"
        ));
        // Child responds
        mockLlm.queueTextResponse("Hello from Simon!");
        // Parent continues with context
        mockLlm.queueTextResponse("Final answer with context.");

        entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Greet me please", null
        ).join();

        List<LlmRequest> history = mockLlm.getRequestHistory();
        assertEquals(3, history.size());

        // Parent's second call (request index 2) should contain full conversation history
        // Linda has prefetchTools=["list_agents"], so first call has prefetch messages too
        LlmRequest parentSecondCall = history.get(2);
        List<LlmMessage> parentMessages = parentSecondCall.messages();

        // Should have: SYSTEM, ASSISTANT(prefetch), TOOL(prefetch), USER, ASSISTANT(tool_use), TOOL(result)
        assertEquals(6, parentMessages.size(),
                "Parent should have SYSTEM + prefetch(ASSISTANT+TOOL) + USER + ASSISTANT(tool_use) + TOOL(result)");
        assertEquals(MessageRole.SYSTEM, parentMessages.get(0).role());

        // The original user message should be preserved (after prefetch messages)
        String originalMessage = extractText(parentMessages.get(3));
        assertEquals("Greet me please", originalMessage,
                "Parent should still have the original user message");

        // The assistant message should contain the delegate_to_agent tool use
        boolean hasToolUse = parentMessages.get(4).content().stream()
                .anyMatch(c -> c instanceof MessageContent.ToolUse tu
                        && "delegate_to_agent".equals(tu.name()));
        assertTrue(hasToolUse, "Parent's assistant message should contain the delegate_to_agent tool use");

        // The tool result should contain the child's response
        String toolResult = parentMessages.get(5).content().stream()
                .filter(c -> c instanceof MessageContent.ToolResult)
                .map(c -> ((MessageContent.ToolResult) c).content())
                .findFirst()
                .orElse(null);
        assertEquals("Hello from Simon!", toolResult,
                "Tool result should contain the child agent's response");
    }

    @Test
    void testMultipleDelegationsPreserveFullHistory() {
        // Parent delegates twice in sequence
        // 1st delegation
        mockLlm.queueToolCallResponse("tc-1", "delegate_to_agent", Map.of(
                "personaId", "simon",
                "message", "First task"
        ));
        mockLlm.queueTextResponse("Simon result 1");
        // After first delegation, parent delegates again
        mockLlm.queueToolCallResponse("tc-2", "delegate_to_agent", Map.of(
                "personaId", "bond",
                "message", "Second task"
        ));
        mockLlm.queueTextResponse("Bond result 2");
        // Parent produces final response
        mockLlm.queueTextResponse("Both tasks complete.");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Do two things", null
        ).join();

        assertEquals("Both tasks complete.", response.response());

        List<LlmRequest> history = mockLlm.getRequestHistory();
        // 5 calls: parent(1st) -> child1 -> parent(2nd) -> child2 -> parent(3rd)
        assertEquals(5, history.size());

        // The 3rd parent call (index 4) should have full history including both delegations
        // Linda has prefetchTools, so 2 extra messages at the start
        LlmRequest finalParentCall = history.get(4);
        List<LlmMessage> messages = finalParentCall.messages();

        // SYSTEM, ASSISTANT(prefetch), TOOL(prefetch), USER, ASSISTANT(tc-1), TOOL(result1), ASSISTANT(tc-2), TOOL(result2)
        assertEquals(8, messages.size(),
                "Final parent call should have full history: SYSTEM + prefetch(ASSISTANT+TOOL) + USER + 2x(ASSISTANT+TOOL)");

        // Verify both delegation tool results are present in the history (plus 1 prefetch)
        long toolResultCount = messages.stream()
                .filter(m -> m.role() == MessageRole.TOOL)
                .count();
        assertEquals(3, toolResultCount,
                "Parent should carry over prefetch + results from both delegations");
    }

    @Test
    void testHandoverDoesNotCarryParentContext() {
        // Parent hands over to child — child gets a fresh context
        mockLlm.queueToolCallResponse("tc-1", "handover_to_agent", Map.of(
                "personaId", "simon",
                "message", "Take over"
        ));
        // Child responds (this becomes the final response)
        mockLlm.queueTextResponse("Simon took over!");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Hand this off", null
        ).join();

        assertEquals("Simon took over!", response.response());

        List<LlmRequest> history = mockLlm.getRequestHistory();
        assertEquals(2, history.size());

        // Child (request 1) should have only SYSTEM + USER, no parent context
        LlmRequest childRequest = history.get(1);
        assertEquals(2, childRequest.messages().size(),
                "Handover child should get fresh context with only SYSTEM + USER");
        assertEquals(MessageRole.SYSTEM, childRequest.messages().get(0).role());
        assertEquals(MessageRole.USER, childRequest.messages().get(1).role());
    }

    @Test
    @DisplayName("Prefetch runs only on first turn, not on session resume")
    void testPrefetchRunsOnlyOnce() {
        // Turn 1: Linda gets prefetch + responds
        mockLlm.queueTextResponse("Hi there!");

        AgentResponse turn1 = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "Hello", null
        ).join();

        // Turn 1 should have prefetch messages in its LLM call
        List<LlmRequest> historyAfterTurn1 = mockLlm.getRequestHistory();
        assertEquals(1, historyAfterTurn1.size());
        LlmRequest firstCall = historyAfterTurn1.get(0);
        // SYSTEM + ASSISTANT(prefetch) + TOOL(prefetch) + USER
        long prefetchToolResults = firstCall.messages().stream()
                .flatMap(m -> m.content().stream())
                .filter(c -> c instanceof MessageContent.ToolResult tr
                        && tr.toolUseId().startsWith("prefetch-"))
                .count();
        assertEquals(1, prefetchToolResults, "First turn should have 1 prefetch tool result");

        // Turn 2: resume session — prefetch should NOT run again
        mockLlm.queueTextResponse("How can I help?");

        entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.DISPATCHER_LINDA,
                "What agents do you have?", turn1.sessionId()
        ).join();

        List<LlmRequest> historyAfterTurn2 = mockLlm.getRequestHistory();
        assertEquals(2, historyAfterTurn2.size());
        LlmRequest secondCall = historyAfterTurn2.get(1);

        // The prefetch result should be carried over from history (not re-executed)
        // Count prefetch tool results — should still be exactly 1 (from turn 1 history)
        long prefetchInTurn2 = secondCall.messages().stream()
                .flatMap(m -> m.content().stream())
                .filter(c -> c instanceof MessageContent.ToolResult tr
                        && tr.toolUseId().startsWith("prefetch-"))
                .count();
        assertEquals(1, prefetchInTurn2,
                "Second turn should carry prefetch from history, not re-execute");
    }

    private String extractText(LlmMessage message) {
        return message.content().stream()
                .filter(c -> c instanceof MessageContent.Text)
                .map(c -> ((MessageContent.Text) c).text())
                .findFirst()
                .orElse(null);
    }
}
