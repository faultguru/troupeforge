package com.troupeforge.integtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("REST API End-to-End Integration Tests")
class RestApiEndToEndTest {

    private static final String BASE_URL =
            System.getProperty("integtest.base-url", "http://localhost:8080");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // ---- Agent Listing ----

    @Test
    @DisplayName("GET /api/agents returns non-empty agent list")
    void listAgentsReturnsAgents() throws Exception {
        HttpResponse<String> response = doGet("/api/agents");

        assertEquals(200, response.statusCode());
        JsonNode json = MAPPER.readTree(response.body());
        assertTrue(json.isArray(), "Response should be an array");
        assertTrue(json.size() > 0, "Should have at least one agent");

        // Check first agent has required fields
        JsonNode first = json.get(0);
        assertTrue(first.has("personaId"), "Agent should have personaId");
        assertTrue(first.has("agentId"), "Agent should have agentId");
        assertFalse(first.path("personaId").asText().isBlank());
    }

    @Test
    @DisplayName("GET /api/agents includes known personas (simon, bond, linda)")
    void listAgentsIncludesKnownPersonas() throws Exception {
        HttpResponse<String> response = doGet("/api/agents");
        assertEquals(200, response.statusCode());

        String body = response.body();
        // Check that known personas appear
        assertTrue(body.contains("simon"), "Should contain simon persona");
        assertTrue(body.contains("bond"), "Should contain bond persona");
        assertTrue(body.contains("linda"), "Should contain linda persona");
    }

    // ---- Status Endpoint ----

    @Test
    @DisplayName("GET /api/status returns UP status with agent count")
    void statusReturnsUp() throws Exception {
        HttpResponse<String> response = doGet("/api/status");

        assertEquals(200, response.statusCode());
        JsonNode json = MAPPER.readTree(response.body());

        assertEquals("UP", json.path("status").asText());
        assertTrue(json.path("agentCount").asInt() > 0, "Should have agents loaded");
        assertTrue(json.has("uptime"), "Should include uptime");
    }

    // ---- Session Management ----

    @Test
    @DisplayName("GET /api/sessions returns session list (initially may be empty)")
    void listSessionsReturns200() throws Exception {
        HttpResponse<String> response = doGet("/api/sessions");

        assertEquals(200, response.statusCode());
        JsonNode json = MAPPER.readTree(response.body());
        assertTrue(json.isArray(), "Response should be an array");
    }

    @Test
    @DisplayName("Chat creates a session that appears in session list")
    void chatCreatesSessionInList() throws Exception {
        // First, chat to create a session
        String sessionId = chatAndGetSessionId("simon", "Testing session creation");

        // Then check sessions list
        HttpResponse<String> response = doGet("/api/sessions");
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains(sessionId), "Session list should contain the created session ID");
    }

    @Test
    @DisplayName("DELETE /api/sessions/{id} removes a session")
    void deleteSessionRemovesIt() throws Exception {
        // Create a session via chat
        String sessionId = chatAndGetSessionId("simon", "Session to delete");

        // Delete it
        HttpResponse<String> deleteResponse = doDelete("/api/sessions/" + sessionId);
        assertEquals(200, deleteResponse.statusCode());

        // Verify it's gone
        HttpResponse<String> listResponse = doGet("/api/sessions");
        assertFalse(listResponse.body().contains(sessionId),
                "Deleted session should not appear in list");
    }

    // ---- Token Usage in Chat Response ----

    @Test
    @DisplayName("Chat response includes token usage and latency")
    void chatResponseIncludesUsageAndLatency() throws Exception {
        HttpResponse<String> response = chatRaw("simon", "Hello for usage test");
        assertEquals(200, response.statusCode());

        JsonNode json = MAPPER.readTree(response.body());

        // Check tokenUsage
        JsonNode usage = json.path("tokenUsage");
        assertFalse(usage.isMissingNode(), "tokenUsage should be present");
        assertTrue(usage.path("inputTokens").asInt() > 0, "inputTokens should be > 0");
        assertTrue(usage.path("outputTokens").asInt() > 0, "outputTokens should be > 0");
        assertTrue(usage.path("totalTokens").asInt() > 0, "totalTokens should be > 0");

        // Check latencyMs
        assertTrue(json.path("latencyMs").asLong() > 0, "latencyMs should be > 0");
    }

    // ---- Multi-turn Conversation ----

    @Test
    @DisplayName("Three-turn conversation maintains context across all turns")
    void threeTurnConversation() throws Exception {
        // Turn 1
        HttpResponse<String> r1 = chatRaw("simon", "Remember the number 42");
        assertEquals(200, r1.statusCode());
        String sessionId = MAPPER.readTree(r1.body()).path("sessionId").asText();

        // Turn 2
        HttpResponse<String> r2 = chatRawWithSession("simon", "Now remember the word 'banana'", sessionId);
        assertEquals(200, r2.statusCode());

        // Turn 3
        HttpResponse<String> r3 = chatRawWithSession("simon", "What number and word did I ask you to remember?", sessionId);
        assertEquals(200, r3.statusCode());
        String response3 = MAPPER.readTree(r3.body()).path("response").asText().toLowerCase();

        assertTrue(response3.contains("42"), "Should remember the number 42");
        assertTrue(response3.contains("banana"), "Should remember the word banana");
    }

    // ---- Concurrent Requests ----

    @Test
    @DisplayName("Concurrent requests to different agents all succeed")
    void concurrentRequests() throws Exception {
        var futures = java.util.List.of(
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return chatRaw("simon", "Concurrent test 1"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }),
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return chatRaw("bond", "Concurrent test 2"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }),
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return chatRaw("simon", "Concurrent test 3"); }
                catch (Exception e) { throw new RuntimeException(e); }
            })
        );

        var allDone = java.util.concurrent.CompletableFuture.allOf(
                futures.toArray(java.util.concurrent.CompletableFuture[]::new));
        allDone.join();

        for (var future : futures) {
            HttpResponse<String> response = future.join();
            assertEquals(200, response.statusCode(), "All concurrent requests should succeed");
            JsonNode json = MAPPER.readTree(response.body());
            assertFalse(json.path("response").asText().isBlank(), "All responses should have content");
        }
    }

    // ---- Helpers ----

    private String chatAndGetSessionId(String personaId, String message) throws Exception {
        HttpResponse<String> response = chatRaw(personaId, message);
        assertEquals(200, response.statusCode());
        return MAPPER.readTree(response.body()).path("sessionId").asText();
    }

    private HttpResponse<String> chatRaw(String personaId, String message) throws Exception {
        return chatRawWithSession(personaId, message, null);
    }

    private HttpResponse<String> chatRawWithSession(String personaId, String message, String sessionId) throws Exception {
        var body = MAPPER.createObjectNode();
        body.put("personaId", personaId);
        body.put("message", message);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .timeout(Duration.ofMinutes(2))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> doGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Accept", "application/json")
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> doDelete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE()
                .timeout(REQUEST_TIMEOUT)
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
