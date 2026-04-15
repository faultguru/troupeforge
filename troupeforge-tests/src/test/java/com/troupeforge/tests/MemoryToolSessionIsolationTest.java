package com.troupeforge.tests;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.tools.memory.MemoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryTool Session Isolation")
class MemoryToolSessionIsolationTest {

    @TempDir
    Path tempDir;

    private MemoryTool tool;
    private ToolContext contextA;
    private ToolContext contextB;

    @BeforeEach
    void setUp() {
        tool = new MemoryTool();

        RequestContext reqCtx = new RequestContext(
                new RequestId("req-iso"),
                new RequestorContext(new UserId("test-user"), new OrganizationId("test-org")),
                StageContext.LIVE,
                Instant.now()
        );
        contextA = new ToolContext(
                reqCtx,
                new AgentSessionId("session-A"),
                new AgentProfileId(new AgentId("agent-1"), new PersonaId("test")),
                tempDir,
                Map.of()
        );
        contextB = new ToolContext(
                reqCtx,
                new AgentSessionId("session-B"),
                new AgentProfileId(new AgentId("agent-1"), new PersonaId("test")),
                tempDir,
                Map.of()
        );
    }

    @Test
    @DisplayName("Set in session A is not visible in session B")
    void setInOneSessionNotVisibleInOther() {
        tool.execute(contextA, new MemoryTool.Request("set", "secret", "alpha"));

        var resp = (MemoryTool.Response) tool.execute(contextB, new MemoryTool.Request("get", "secret", null));
        assertFalse(resp.success(), "Key set in session A should not exist in session B");
        assertNull(resp.value());
    }

    @Test
    @DisplayName("List in session B does not include keys from session A")
    void listKeysIsolatedBetweenSessions() {
        tool.execute(contextA, new MemoryTool.Request("set", "k1", "v1"));
        tool.execute(contextA, new MemoryTool.Request("set", "k2", "v2"));
        tool.execute(contextB, new MemoryTool.Request("set", "k3", "v3"));

        var listA = (MemoryTool.Response) tool.execute(contextA, new MemoryTool.Request("list", null, null));
        var listB = (MemoryTool.Response) tool.execute(contextB, new MemoryTool.Request("list", null, null));

        assertEquals(2, listA.keys().size(), "Session A should have 2 keys");
        assertTrue(listA.keys().contains("k1"));
        assertTrue(listA.keys().contains("k2"));

        assertEquals(1, listB.keys().size(), "Session B should have 1 key");
        assertTrue(listB.keys().contains("k3"));
        assertFalse(listB.keys().contains("k1"));
    }

    @Test
    @DisplayName("Delete in session A does not affect session B")
    void deleteInOneSessionDoesNotAffectOther() {
        tool.execute(contextA, new MemoryTool.Request("set", "shared-name", "val-A"));
        tool.execute(contextB, new MemoryTool.Request("set", "shared-name", "val-B"));

        tool.execute(contextA, new MemoryTool.Request("delete", "shared-name", null));

        var respA = (MemoryTool.Response) tool.execute(contextA, new MemoryTool.Request("get", "shared-name", null));
        var respB = (MemoryTool.Response) tool.execute(contextB, new MemoryTool.Request("get", "shared-name", null));

        assertFalse(respA.success(), "Key should be deleted in session A");
        assertTrue(respB.success(), "Key should still exist in session B");
        assertEquals("val-B", respB.value());
    }

    @Test
    @DisplayName("Same key name in different sessions holds different values")
    void sameKeyDifferentValuesAcrossSessions() {
        tool.execute(contextA, new MemoryTool.Request("set", "color", "red"));
        tool.execute(contextB, new MemoryTool.Request("set", "color", "blue"));

        var respA = (MemoryTool.Response) tool.execute(contextA, new MemoryTool.Request("get", "color", null));
        var respB = (MemoryTool.Response) tool.execute(contextB, new MemoryTool.Request("get", "color", null));

        assertEquals("red", respA.value());
        assertEquals("blue", respB.value());
    }
}
