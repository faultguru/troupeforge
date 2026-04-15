package com.troupeforge.tests;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.tests.support.TestBucketHelper;
import com.troupeforge.tests.support.TestSpringConfig;
import com.troupeforge.tools.delegation.ListAgentsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("ListAgentsTool Filter")
class ListAgentsFilterTest {

    @Autowired
    private AgentBucketRegistry bucketRegistry;

    @Autowired
    private ListAgentsTool listAgentsTool;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        TestBucketHelper.loadTestBucket(bucketRegistry);

        RequestContext reqCtx = new RequestContext(
                RequestId.generate(),
                new RequestorContext(new UserId("test"), TestBucketHelper.TEST_ORG),
                TestBucketHelper.TEST_STAGE,
                Instant.now()
        );
        toolContext = new ToolContext(reqCtx, new AgentSessionId("test-session"),
                TestBucketHelper.DISPATCHER_LINDA, Path.of("."), Map.of());
    }

    private ListAgentsTool.Response callWithFilter(String filter) {
        return (ListAgentsTool.Response) listAgentsTool.execute(toolContext,
                new ListAgentsTool.Request(filter));
    }

    private List<String> personaIds(ListAgentsTool.Response response) {
        return response.agents().stream().map(ListAgentsTool.AgentEntry::personaId).toList();
    }

    @Test
    @DisplayName("No filter returns all agents")
    void testNoFilter() {
        var response = callWithFilter(null);
        assertFalse(response.agents().isEmpty(), "Should return all agents");
        assertTrue(response.agents().size() >= 10, "Should have at least 10 agents");
    }

    @Test
    @DisplayName("Filter by exact personaId returns it first")
    void testExactPersonaIdMatch() {
        var response = callWithFilter("bond");
        var ids = personaIds(response);
        assertFalse(ids.isEmpty(), "Should find bond");
        assertEquals("bond", ids.get(0), "Exact personaId match should be first");
    }

    @Test
    @DisplayName("Filter by personaId partial match")
    void testPartialPersonaIdMatch() {
        var response = callWithFilter("bon");
        var ids = personaIds(response);
        assertTrue(ids.contains("bond"), "Should find bond with partial personaId 'bon'");
    }

    @Test
    @DisplayName("Filter by display name finds agent")
    void testDisplayNameMatch() {
        var response = callWithFilter("James Bond");
        var ids = personaIds(response);
        assertTrue(ids.contains("bond"), "Should find bond by display name");
    }

    @Test
    @DisplayName("Filter by agentId finds agents in that group")
    void testAgentIdMatch() {
        var response = callWithFilter("greeter");
        var ids = personaIds(response);
        assertTrue(ids.contains("bond"), "Greeter group should include bond");
        assertTrue(ids.contains("lord"), "Greeter group should include lord");
        assertTrue(ids.contains("simon"), "Greeter group should include simon");
    }

    @Test
    @DisplayName("Filter by description keyword")
    void testDescriptionMatch() {
        var response = callWithFilter("arithmetic");
        var ids = personaIds(response);
        assertTrue(ids.contains("albert"), "Should find albert agent by description containing 'arithmetic'");
    }

    @Test
    @DisplayName("PersonaId match has higher priority than description match")
    void testPriorityOrdering() {
        // "lord" matches personaId "lord" exactly, should be first
        var response = callWithFilter("lord");
        var ids = personaIds(response);
        assertFalse(ids.isEmpty());
        assertEquals("lord", ids.get(0), "Exact personaId should be first");
    }

    @Test
    @DisplayName("Filter is case-insensitive")
    void testCaseInsensitive() {
        var response = callWithFilter("BOND");
        var ids = personaIds(response);
        assertTrue(ids.contains("bond"), "Filter should be case-insensitive");
    }

    @Test
    @DisplayName("No match returns empty list")
    void testNoMatch() {
        var response = callWithFilter("zzzznonexistent");
        assertTrue(response.agents().isEmpty(), "Should return empty for no match");
    }
}
