package com.troupeforge.tests;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.tools.web.WebFetchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebFetchTool")
class WebFetchToolTest {

    @TempDir
    Path tempDir;

    private WebFetchTool tool;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        tool = new WebFetchTool();
        RequestContext reqCtx = new RequestContext(
                new RequestId("req-web"),
                new RequestorContext(new UserId("test-user"), new OrganizationId("test-org")),
                StageContext.LIVE,
                Instant.now()
        );
        toolContext = new ToolContext(
                reqCtx,
                new AgentSessionId("session-web"),
                new AgentProfileId(new AgentId("agent-web"), new PersonaId("test")),
                tempDir,
                Map.of()
        );
    }

    @Test
    @DisplayName("Invalid URL returns error response with status 0")
    void invalidUrlReturnsError() {
        var request = new WebFetchTool.Request("not-a-valid-url", null, null, null);
        var response = (WebFetchTool.Response) tool.execute(toolContext, request);

        assertEquals(0, response.statusCode(), "Invalid URL should yield status code 0");
        assertEquals("error", response.contentType());
        assertTrue(response.body().contains("Error fetching URL"));
    }

    @Test
    @DisplayName("Unreachable host returns error response")
    void unreachableHostReturnsError() {
        var request = new WebFetchTool.Request("http://localhost:19999/nonexistent", null, null, 2);
        var response = (WebFetchTool.Response) tool.execute(toolContext, request);

        assertEquals(0, response.statusCode());
        assertEquals("error", response.contentType());
        assertNotNull(response.body());
    }

    @Test
    @DisplayName("Tool metadata is correct")
    void toolMetadataIsCorrect() {
        assertEquals("web_fetch", tool.name());
        assertEquals(WebFetchTool.Request.class, tool.requestType());
        assertEquals(WebFetchTool.Response.class, tool.responseType());
    }

    @Test
    @DisplayName("Default method is GET when method is null")
    void defaultMethodIsGet() {
        // We test this indirectly: a request with no method and an invalid URL
        // should not throw a NullPointerException on the method field
        var request = new WebFetchTool.Request("http://localhost:19999/test", null, null, 1);
        var response = (WebFetchTool.Response) tool.execute(toolContext, request);

        // Should get a connection error, not an NPE
        assertEquals(0, response.statusCode());
        assertTrue(response.body().startsWith("Error fetching URL"));
    }
}
