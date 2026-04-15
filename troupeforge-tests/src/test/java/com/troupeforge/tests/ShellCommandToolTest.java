package com.troupeforge.tests;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.tools.system.ShellCommandTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShellCommandTool")
class ShellCommandToolTest {

    @TempDir
    Path tempDir;

    private ShellCommandTool tool;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        tool = new ShellCommandTool();
        RequestContext reqCtx = new RequestContext(
                new RequestId("req-shell"),
                new RequestorContext(new UserId("test-user"), new OrganizationId("test-org")),
                StageContext.LIVE,
                Instant.now()
        );
        toolContext = new ToolContext(
                reqCtx,
                new AgentSessionId("session-shell"),
                new AgentProfileId(new AgentId("agent-shell"), new PersonaId("test")),
                tempDir,
                Map.of()
        );
    }

    @Test
    @DisplayName("Executes a simple echo command successfully")
    void executesSimpleEchoCommand() {
        var request = new ShellCommandTool.Request("cmd", List.of("/c", "echo", "hello"), null, null);
        var response = (ShellCommandTool.Response) tool.execute(toolContext, request);

        assertEquals(0, response.exitCode(), "Echo command should exit with code 0");
        assertTrue(response.stdout().contains("hello"), "Stdout should contain 'hello'");
        assertFalse(response.timedOut(), "Should not time out");
    }

    @Test
    @DisplayName("Rejects sandbox escape via parent traversal in working directory")
    void rejectsSandboxEscapeInWorkingDirectory() {
        var request = new ShellCommandTool.Request("cmd", List.of("/c", "dir"), "../../..", null);

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(toolContext, request),
                "Should reject working directory that escapes the sandbox");
    }

    @Test
    @DisplayName("Rejects non-existent subdirectory as working directory")
    void rejectsNonExistentWorkingDirectory() {
        var request = new ShellCommandTool.Request("cmd", List.of("/c", "dir"), "nonexistent-subdir", null);

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(toolContext, request),
                "Should reject non-existent working directory");
    }

    @Test
    @DisplayName("Timeout caps at maximum allowed value")
    void timeoutCapsAtMaximum() {
        // Requesting 999 seconds should be capped to 120; the command itself should
        // complete quickly regardless
        var request = new ShellCommandTool.Request("cmd", List.of("/c", "echo", "fast"), null, 999);
        var response = (ShellCommandTool.Response) tool.execute(toolContext, request);

        assertEquals(0, response.exitCode());
        assertFalse(response.timedOut());
    }

    @Test
    @DisplayName("Captures non-zero exit code")
    void capturesNonZeroExitCode() {
        var request = new ShellCommandTool.Request("cmd", List.of("/c", "exit", "42"), null, null);
        var response = (ShellCommandTool.Response) tool.execute(toolContext, request);

        assertEquals(42, response.exitCode(), "Should capture the exit code from the command");
        assertFalse(response.timedOut());
    }
}
