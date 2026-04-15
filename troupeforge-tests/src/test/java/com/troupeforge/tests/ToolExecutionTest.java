package com.troupeforge.tests;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.tools.file.*;
import com.troupeforge.tools.memory.MemoryTool;
import com.troupeforge.tools.reasoning.ThinkTool;
import com.troupeforge.tools.util.CalculatorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tool Execution")
class ToolExecutionTest {

    @TempDir
    Path tempDir;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        RequestContext reqCtx = new RequestContext(
                new RequestId("req-1"),
                new RequestorContext(new UserId("test-user"), new OrganizationId("test-org")),
                StageContext.LIVE,
                Instant.now()
        );
        toolContext = new ToolContext(
                reqCtx,
                new AgentSessionId("session-1"),
                new AgentProfileId(new AgentId("test-agent"), new PersonaId("test")),
                tempDir,
                Map.of()
        );
    }

    // --- SearchFilesTool ---

    @Test
    @DisplayName("SearchFiles finds matching pattern in files")
    void testSearchFilesFindsPattern() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "Hello World\nGoodbye World\n");
        Files.writeString(tempDir.resolve("data.txt"), "Some data\nHello Again\n");

        SearchFilesTool tool = new SearchFilesTool();
        var request = new SearchFilesTool.Request("Hello", null, null, null, null);
        var response = (SearchFilesTool.Response) tool.execute(toolContext, request);

        assertEquals(2, response.totalMatches(), "Should find 2 matches across files");
        assertFalse(response.truncated());
    }

    @Test
    @DisplayName("SearchFiles respects maxResults")
    void testSearchFilesRespectsMaxResults() throws IOException {
        // Create file with many matching lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("match line ").append(i).append("\n");
        }
        Files.writeString(tempDir.resolve("many.txt"), sb.toString());

        SearchFilesTool tool = new SearchFilesTool();
        var request = new SearchFilesTool.Request("match", null, 5, null, null);
        var response = (SearchFilesTool.Response) tool.execute(toolContext, request);

        assertEquals(5, response.matches().size(), "Should return at most 5 matches");
        assertEquals(20, response.totalMatches(), "Total matches should be 20");
        assertTrue(response.truncated(), "Should be truncated");
    }

    // --- WriteFileTool ---

    @Test
    @DisplayName("WriteFile creates a new file")
    void testWriteFileCreatesFile() {
        WriteFileTool tool = new WriteFileTool();
        var request = new WriteFileTool.Request("output.txt", "Hello World", null);
        var response = (WriteFileTool.Response) tool.execute(toolContext, request);

        assertTrue(response.created(), "File should be reported as created");
        assertTrue(Files.exists(tempDir.resolve("output.txt")));
        assertEquals("output.txt", response.path());
    }

    @Test
    @DisplayName("WriteFile rejects sandbox escape via path traversal")
    void testWriteFileRejectsSandboxEscape() {
        WriteFileTool tool = new WriteFileTool();
        var request = new WriteFileTool.Request("../../escape.txt", "malicious", null);

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(toolContext, request),
                "Should reject path traversal");
    }

    // --- CalculatorTool ---

    @Test
    @DisplayName("Calculator performs addition")
    void testCalculatorAdd() {
        CalculatorTool tool = new CalculatorTool();
        var request = new CalculatorTool.Request("add", 3.0, 4.0);
        var response = (CalculatorTool.Response) tool.execute(toolContext, request);

        assertEquals(7.0, response.result(), 0.001);
    }

    @Test
    @DisplayName("Calculator performs division")
    void testCalculatorDivide() {
        CalculatorTool tool = new CalculatorTool();
        var request = new CalculatorTool.Request("divide", 10.0, 3.0);
        var response = (CalculatorTool.Response) tool.execute(toolContext, request);

        assertEquals(10.0 / 3.0, response.result(), 0.001);
    }

    @Test
    @DisplayName("Calculator handles division by zero")
    void testCalculatorDivideByZero() {
        CalculatorTool tool = new CalculatorTool();
        var request = new CalculatorTool.Request("divide", 10.0, 0.0);
        var response = (CalculatorTool.Response) tool.execute(toolContext, request);

        assertTrue(Double.isNaN(response.result()), "Division by zero should return NaN");
        assertTrue(response.expression().contains("Error"), "Expression should indicate error");
    }

    // --- ThinkTool ---

    @Test
    @DisplayName("ThinkTool acknowledges thought")
    void testThinkToolAcknowledges() {
        ThinkTool tool = new ThinkTool();
        var request = new ThinkTool.Request("Let me think about this problem...");
        var response = (ThinkTool.Response) tool.execute(toolContext, request);

        assertTrue(response.acknowledged(), "Think tool should acknowledge");
    }

    // --- MemoryTool ---

    @Test
    @DisplayName("Memory set and get")
    void testMemorySetAndGet() {
        MemoryTool tool = new MemoryTool();

        var setReq = new MemoryTool.Request("set", "myKey", "myValue");
        var setResp = (MemoryTool.Response) tool.execute(toolContext, setReq);
        assertTrue(setResp.success(), "Set should succeed");

        var getReq = new MemoryTool.Request("get", "myKey", null);
        var getResp = (MemoryTool.Response) tool.execute(toolContext, getReq);
        assertTrue(getResp.success(), "Get should succeed");
        assertEquals("myValue", getResp.value());
    }

    @Test
    @DisplayName("Memory list keys")
    void testMemoryList() {
        MemoryTool tool = new MemoryTool();

        tool.execute(toolContext, new MemoryTool.Request("set", "key1", "val1"));
        tool.execute(toolContext, new MemoryTool.Request("set", "key2", "val2"));

        var listResp = (MemoryTool.Response) tool.execute(toolContext,
                new MemoryTool.Request("list", null, null));
        assertTrue(listResp.success());
        assertNotNull(listResp.keys());
        assertTrue(listResp.keys().contains("key1"));
        assertTrue(listResp.keys().contains("key2"));
    }

    @Test
    @DisplayName("Memory delete key")
    void testMemoryDelete() {
        MemoryTool tool = new MemoryTool();

        tool.execute(toolContext, new MemoryTool.Request("set", "toDelete", "val"));
        var delResp = (MemoryTool.Response) tool.execute(toolContext,
                new MemoryTool.Request("delete", "toDelete", null));
        assertTrue(delResp.success());

        var getResp = (MemoryTool.Response) tool.execute(toolContext,
                new MemoryTool.Request("get", "toDelete", null));
        assertFalse(getResp.success(), "Getting deleted key should fail");
    }

    // --- ListFilesTool ---

    @Test
    @DisplayName("ListFiles lists files in directory")
    void testListFilesInDirectory() throws IOException {
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        Files.createDirectory(tempDir.resolve("subdir"));

        ListFilesTool tool = new ListFilesTool();
        var request = new ListFilesTool.Request(null, null, null);
        var response = (ListFilesTool.Response) tool.execute(toolContext, request);

        assertNotNull(response.entries());
        assertTrue(response.entries().size() >= 3, "Should list at least 3 entries (2 files + 1 dir)");
    }

    // --- HeadFileTool ---

    @Test
    @DisplayName("HeadFile reads first N lines")
    void testHeadFileReadsLines() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        Files.writeString(tempDir.resolve("lines.txt"), sb.toString());

        HeadFileTool tool = new HeadFileTool();
        var request = new HeadFileTool.Request(List.of("lines.txt"), null, 5);
        var response = (HeadFileTool.Response) tool.execute(toolContext, request);

        assertNotNull(response.results());
        assertEquals(1, response.results().size());
        assertEquals(5, response.results().get(0).lineCount());
        assertTrue(response.results().get(0).content().contains("Line 1"));
        assertTrue(response.results().get(0).content().contains("Line 5"));
        assertFalse(response.results().get(0).content().contains("Line 6"));
    }

    // --- ReadFileTool ---

    @Test
    @DisplayName("ReadFile reads full file content")
    void testReadFileContent() throws IOException {
        String content = "Full file content\nLine 2\nLine 3";
        Files.writeString(tempDir.resolve("full.txt"), content);

        ReadFileTool tool = new ReadFileTool();
        var request = new ReadFileTool.Request(List.of("full.txt"), null);
        var response = (ReadFileTool.Response) tool.execute(toolContext, request);

        assertNotNull(response.files());
        assertEquals(1, response.files().size());
        assertEquals(content, response.files().get(0).content());
    }
}
