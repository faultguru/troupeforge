package com.troupeforge.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.engine.tool.ToolSchemaGenerator;
import com.troupeforge.tools.file.HeadFileTool;
import com.troupeforge.tools.file.ListFilesTool;
import com.troupeforge.tools.file.ReadFileTool;
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

@DisplayName("File Tools")
class FileToolTest {

    private static final OrganizationId TEST_ORG = new OrganizationId("test-org");
    private static final AgentProfileId MOCK_AGENT_CLARA =
            new AgentProfileId(new AgentId("mock-agent"), new PersonaId("clara"));

    private HeadFileTool headFileTool;
    private ListFilesTool listFilesTool;
    private ReadFileTool readFileTool;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        headFileTool = new HeadFileTool();
        listFilesTool = new ListFilesTool();
        readFileTool = new ReadFileTool();
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Files.writeString(tempDir.resolve("hello.txt"), "line1\nline2\nline3\nline4\nline5\n");
        Files.writeString(tempDir.resolve("data.csv"), "name,age\nalice,30\nbob,25\n");
        Files.writeString(tempDir.resolve("notes.md"), "# Notes\nSome notes here.\n");
        Files.writeString(tempDir.resolve("config.json"), "{\"key\": \"value\"}\n");

        Path subDir = tempDir.resolve("src");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("main.java"), "public class Main {}\n");
        Files.writeString(subDir.resolve("test.java"), "public class Test {}\n");
        Files.writeString(subDir.resolve("readme.txt"), "Source readme\n");
    }

    private ToolContext makeToolContext() {
        RequestContext reqCtx = new RequestContext(
                RequestId.generate(),
                new RequestorContext(new UserId("test-user"), TEST_ORG),
                StageContext.LIVE, Instant.now()
        );
        return new ToolContext(reqCtx, null, MOCK_AGENT_CLARA, tempDir, Map.of());
    }

    // ---- HeadFileTool ----

    @Test
    void testHeadFileWithExplicitPaths() {
        var req = new HeadFileTool.Request(List.of("hello.txt"), null, 3);
        var response = (HeadFileTool.Response) headFileTool.execute(makeToolContext(), req);

        assertEquals(1, response.results().size());
        var head = response.results().get(0);
        assertEquals("hello.txt", head.path());
        assertEquals(3, head.lineCount());
        assertEquals("line1\nline2\nline3", head.content());
    }

    @Test
    void testHeadFileDefaultLineCount() {
        var req = new HeadFileTool.Request(List.of("hello.txt"), null, null);
        var response = (HeadFileTool.Response) headFileTool.execute(makeToolContext(), req);

        assertEquals(1, response.results().size());
        assertTrue(response.results().get(0).lineCount() <= 10);
    }

    @Test
    void testHeadFileWithRegexPattern() {
        var req = new HeadFileTool.Request(null, ".*\\.txt", 2);
        var response = (HeadFileTool.Response) headFileTool.execute(makeToolContext(), req);

        assertTrue(response.results().size() >= 2,
                "Should find at least hello.txt and src/readme.txt");

        var paths = response.results().stream().map(HeadFileTool.FileHead::path).toList();
        assertTrue(paths.stream().anyMatch(p -> p.contains("hello.txt")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("readme.txt")));

        for (var head : response.results()) {
            assertTrue(head.lineCount() <= 2);
        }
    }

    @Test
    void testHeadFileBatchWithMultiplePaths() {
        var req = new HeadFileTool.Request(List.of("hello.txt", "data.csv", "notes.md"), null, 1);
        var response = (HeadFileTool.Response) headFileTool.execute(makeToolContext(), req);

        assertEquals(3, response.results().size());
        assertEquals("line1", response.results().get(0).content());
        assertEquals("name,age", response.results().get(1).content());
        assertEquals("# Notes", response.results().get(2).content());
    }

    @Test
    void testHeadFilePathTraversalBlocked() {
        var req = new HeadFileTool.Request(List.of("../../etc/passwd"), null, 5);
        var response = (HeadFileTool.Response) headFileTool.execute(makeToolContext(), req);

        assertEquals(0, response.results().size());
    }

    // ---- ReadFileTool ----

    @Test
    void testReadFileWithExplicitPath() {
        var req = new ReadFileTool.Request(List.of("config.json"), null);
        var response = (ReadFileTool.Response) readFileTool.execute(makeToolContext(), req);

        assertEquals(1, response.files().size());
        assertEquals("{\"key\": \"value\"}\n", response.files().get(0).content());
    }

    @Test
    void testReadFileBatchWithMultiplePaths() {
        var req = new ReadFileTool.Request(List.of("hello.txt", "notes.md"), null);
        var response = (ReadFileTool.Response) readFileTool.execute(makeToolContext(), req);

        assertEquals(2, response.files().size());
        assertTrue(response.files().get(0).content().startsWith("line1"));
        assertTrue(response.files().get(1).content().startsWith("# Notes"));
    }

    @Test
    void testReadFileWithRegexPattern() {
        var req = new ReadFileTool.Request(null, ".*\\.java");
        var response = (ReadFileTool.Response) readFileTool.execute(makeToolContext(), req);

        assertEquals(2, response.files().size());
        var paths = response.files().stream().map(ReadFileTool.FileContent::path).toList();
        assertTrue(paths.stream().anyMatch(p -> p.contains("main.java")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("test.java")));
    }

    @Test
    void testReadFileWithPathsAndPattern() {
        var req = new ReadFileTool.Request(List.of("config.json"), ".*\\.csv");
        var response = (ReadFileTool.Response) readFileTool.execute(makeToolContext(), req);

        assertEquals(2, response.files().size());
        var paths = response.files().stream().map(ReadFileTool.FileContent::path).toList();
        assertTrue(paths.stream().anyMatch(p -> p.contains("config.json")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("data.csv")));
    }

    @Test
    void testReadFileNonExistentPath() {
        var req = new ReadFileTool.Request(List.of("nonexistent.txt"), null);
        var response = (ReadFileTool.Response) readFileTool.execute(makeToolContext(), req);

        assertEquals(1, response.files().size());
        assertEquals("[not a file]", response.files().get(0).content());
    }

    // ---- ListFilesTool ----

    @Test
    void testListFilesDefaultDirectory() {
        var req = new ListFilesTool.Request(null, null, null);
        var response = (ListFilesTool.Response) listFilesTool.execute(makeToolContext(), req);

        assertTrue(response.entries().size() >= 5,
                "Should list at least 4 files + src/ directory");

        var names = response.entries().stream().map(ListFilesTool.FileEntry::path).toList();
        assertTrue(names.stream().anyMatch(p -> p.contains("hello.txt")));
        assertTrue(names.stream().anyMatch(p -> p.contains("src")));
    }

    @Test
    void testListFilesRecursive() {
        var req = new ListFilesTool.Request(null, null, true);
        var response = (ListFilesTool.Response) listFilesTool.execute(makeToolContext(), req);

        var paths = response.entries().stream().map(ListFilesTool.FileEntry::path).toList();
        assertTrue(paths.stream().anyMatch(p -> p.contains("main.java")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("test.java")));
    }

    @Test
    void testListFilesWithRegexFilter() {
        var req = new ListFilesTool.Request(null, ".*\\.txt", false);
        var response = (ListFilesTool.Response) listFilesTool.execute(makeToolContext(), req);

        assertEquals(1, response.entries().size());
        assertTrue(response.entries().get(0).path().contains("hello.txt"));
    }

    @Test
    void testListFilesRecursiveWithRegex() {
        var req = new ListFilesTool.Request(null, ".*\\.txt", true);
        var response = (ListFilesTool.Response) listFilesTool.execute(makeToolContext(), req);

        assertEquals(2, response.entries().size());
    }

    @Test
    void testListFilesSpecificSubdirectory() {
        var req = new ListFilesTool.Request(List.of("src"), null, false);
        var response = (ListFilesTool.Response) listFilesTool.execute(makeToolContext(), req);

        assertEquals(3, response.entries().size());
        var paths = response.entries().stream().map(ListFilesTool.FileEntry::path).toList();
        assertTrue(paths.stream().anyMatch(p -> p.contains("main.java")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("test.java")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("readme.txt")));
    }

    @Test
    void testListFilesReportsSize() {
        var req = new ListFilesTool.Request(List.of("src"), ".*\\.java", false);
        var response = (ListFilesTool.Response) listFilesTool.execute(makeToolContext(), req);

        for (var entry : response.entries()) {
            assertFalse(entry.directory());
            assertTrue(entry.size() > 0, "File size should be > 0");
        }
    }

    // ---- Schema generation ----

    @Test
    void testHeadFileSchemaGeneration() {
        var schema = ToolSchemaGenerator.generateSchema(HeadFileTool.Request.class);

        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("paths"));
        assertTrue(properties.containsKey("pattern"));
        assertTrue(properties.containsKey("lines"));

        @SuppressWarnings("unchecked")
        var required = (List<String>) schema.get("required");
        assertFalse(required.contains("paths"));
        assertFalse(required.contains("pattern"));
        assertFalse(required.contains("lines"));
    }

    @Test
    void testReadFileSchemaGeneration() {
        var schema = ToolSchemaGenerator.generateSchema(ReadFileTool.Request.class);

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("paths"));
        assertTrue(properties.containsKey("pattern"));

        @SuppressWarnings("unchecked")
        var required = (List<String>) schema.get("required");
        assertTrue(required.isEmpty());
    }

    // ---- ObjectMapper round-trip ----

    @Test
    void testRequestDeserialization() {
        Map<String, Object> raw = Map.of(
                "paths", List.of("a.txt", "b.txt"),
                "lines", 5
        );
        var req = objectMapper.convertValue(raw, HeadFileTool.Request.class);

        assertEquals(List.of("a.txt", "b.txt"), req.paths());
        assertEquals(5, req.lines());
        assertNull(req.pattern());
    }

    @Test
    void testResponseSerialization() throws Exception {
        var response = new HeadFileTool.Response(List.of(
                new HeadFileTool.FileHead("test.txt", "hello world", 1)
        ));
        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("test.txt"));
        assertTrue(json.contains("hello world"));
        assertTrue(json.contains("\"lineCount\":1"));
    }
}
