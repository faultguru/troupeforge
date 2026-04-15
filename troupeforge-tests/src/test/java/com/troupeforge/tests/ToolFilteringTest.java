package com.troupeforge.tests;

import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.llm.LlmRequest;
import com.troupeforge.core.llm.ToolDefinition;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Tool Filtering")
class ToolFilteringTest {

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
    @DisplayName("Agent with specific tools config only sees allowed tools")
    void testAgentOnlySeesAllowedTools() {
        // mock-agent:clara has tools: head_file, list_files, read_file
        mockLlm.queueTextResponse("Analysis complete");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.MOCK_AGENT_CLARA,
                "Analyze files", null
        ).join();

        assertNotNull(response);
        LlmRequest llmRequest = mockLlm.getLastRequest();
        Set<String> toolNames = llmRequest.tools().stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toSet());

        // Should have the allowed file tools
        assertTrue(toolNames.contains("head_file"), "Should have head_file tool");
        assertTrue(toolNames.contains("list_files"), "Should have list_files tool");
        assertTrue(toolNames.contains("read_file"), "Should have read_file tool");

        // Should NOT have delegation or other tools
        assertFalse(toolNames.contains("delegate_to_agent"), "Should NOT have delegate_to_agent tool");
        assertFalse(toolNames.contains("calculator"), "Should NOT have calculator tool");
        assertFalse(toolNames.contains("write_file"), "Should NOT have write_file tool");
    }

    @Test
    @DisplayName("Agent with empty tools list gets no tools")
    void testEmptyToolListMeansNoTools() {
        // echo:pete has tools: [] (empty list) - should get NO tools
        mockLlm.queueTextResponse("Echo response");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.ECHO_PETE,
                "Hello", null
        ).join();

        assertNotNull(response);
        LlmRequest llmRequest = mockLlm.getLastRequest();

        // Echo agent has "tools": {"values": []} which resolves to an empty set.
        // Empty set means NO tools available to the agent.
        assertTrue(llmRequest.tools().isEmpty(),
                "Agent with empty tools config should get no tools, but got: "
                + llmRequest.tools().stream().map(ToolDefinition::name).toList());
    }

    @Test
    @DisplayName("Tool filtering is reflected in LLM request tool definitions")
    void testToolFilteringInLlmRequest() {
        // calculator:albert has tools: calculator, think, memory
        mockLlm.queueTextResponse("Result is 42");

        AgentResponse response = entryPoint.submit(
                requestor, TestBucketHelper.TEST_STAGE,
                TestBucketHelper.CALCULATOR_ALBERT,
                "What is 6 * 7?", null
        ).join();

        assertNotNull(response);
        LlmRequest llmRequest = mockLlm.getLastRequest();
        Set<String> toolNames = llmRequest.tools().stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("calculator"), "Should have calculator tool");
        assertTrue(toolNames.contains("think"), "Should have think tool");
        assertTrue(toolNames.contains("memory"), "Should have memory tool");

        // Should NOT have file or delegation tools
        assertFalse(toolNames.contains("head_file"), "Should NOT have head_file tool");
        assertFalse(toolNames.contains("delegate_to_agent"), "Should NOT have delegate_to_agent tool");
    }
}
