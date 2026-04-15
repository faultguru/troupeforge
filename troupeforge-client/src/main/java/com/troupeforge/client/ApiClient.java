package com.troupeforge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles all HTTP communication with the TroupeForge server.
 */
public class ApiClient {

    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private boolean debugMode = false;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    // --- Records ---

    public record TokenUsage(int inputTokens, int outputTokens, int totalTokens) {}

    public record InferenceSummary(String personaId, String model, long latencyMs,
                                    int inputTokens, int outputTokens, int totalTokens) {}

    public record ChatResult(
            String personaId,
            String response,
            String sessionId,
            String requestId,
            TokenUsage tokenUsage,
            List<InferenceSummary> inferences,
            long latencyMs
    ) {}

    public record AgentInfo(
            String personaId,
            String agentId,
            String name,
            String displayName,
            String description,
            String type
    ) {}

    public record SessionInfo(
            String sessionId,
            String agentId,
            String personaId,
            String startedAt,
            int historySize
    ) {}

    public record StatusInfo(
            String status,
            int agentCount,
            int activeSessionCount,
            String uptime
    ) {}

    // --- API Methods ---

    /**
     * Send a chat message to an agent.
     */
    public ChatResult send(String personaId, String message, String sessionId) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("personaId", personaId);
        body.put("message", message);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }

        String requestJson = mapper.writeValueAsString(body);
        if (debugMode) {
            System.err.println("[DEBUG] POST " + baseUrl + "/api/chat");
            System.err.println("[DEBUG] Request: " + requestJson);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long clientLatency = System.currentTimeMillis() - startTime;

        if (debugMode) {
            System.err.println("[DEBUG] Response (" + response.statusCode() + "): " + response.body());
        }

        if (response.statusCode() != 200) {
            String errorMsg = parseErrorMessage(response.statusCode(), response.body());
            throw new RuntimeException(errorMsg);
        }

        JsonNode json = mapper.readTree(response.body());

        TokenUsage usage = null;
        JsonNode usageNode = json.path("tokenUsage");
        if (!usageNode.isMissingNode() && !usageNode.isNull()) {
            usage = new TokenUsage(
                    usageNode.path("inputTokens").asInt(0),
                    usageNode.path("outputTokens").asInt(0),
                    usageNode.path("totalTokens").asInt(0)
            );
        }

        long latency = json.path("latencyMs").asLong(clientLatency);

        List<InferenceSummary> inferences = new ArrayList<>();
        JsonNode infNode = json.path("inferences");
        if (infNode.isArray()) {
            for (JsonNode inf : infNode) {
                inferences.add(new InferenceSummary(
                        inf.path("personaId").asText(""),
                        inf.path("model").asText(""),
                        inf.path("latencyMs").asLong(0),
                        inf.path("inputTokens").asInt(0),
                        inf.path("outputTokens").asInt(0),
                        inf.path("totalTokens").asInt(0)
                ));
            }
        }

        return new ChatResult(
                json.path("personaId").asText(personaId),
                json.path("response").asText(),
                json.path("sessionId").asText(null),
                json.path("requestId").asText(null),
                usage,
                inferences,
                latency
        );
    }

    /**
     * Send a streaming chat message. Calls onDelta for each text chunk.
     * Returns the final ChatResult when the stream completes.
     */
    public ChatResult sendStreaming(String personaId, String message, String sessionId,
                                     Consumer<String> onDelta) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("personaId", personaId);
        body.put("message", message);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }

        String requestJson = mapper.writeValueAsString(body);
        if (debugMode) {
            System.err.println("[DEBUG] POST " + baseUrl + "/api/chat/stream");
            System.err.println("[DEBUG] Request: " + requestJson);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat/stream"))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<java.io.InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            try (java.io.InputStream is = response.body()) {
                String errorBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException(parseErrorMessage(response.statusCode(), errorBody));
            }
        }

        StringBuilder fullResponse = new StringBuilder();
        int totalTokens = 0;
        long latencyMs = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    try {
                        JsonNode event = mapper.readTree(data);
                        // Check event type
                        if (event.has("text")) {
                            String text = event.get("text").asText();
                            fullResponse.append(text);
                            onDelta.accept(text);
                        } else if (event.has("totalTokens")) {
                            totalTokens = event.path("totalTokens").asInt(0);
                            latencyMs = event.path("latencyMs").asLong(0);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        long clientLatency = System.currentTimeMillis() - startTime;
        if (latencyMs == 0) latencyMs = clientLatency;

        return new ChatResult(
                personaId,
                fullResponse.toString(),
                null, // sessionId not available from stream currently
                null,
                totalTokens > 0 ? new TokenUsage(0, 0, totalTokens) : null,
                List.of(),
                latencyMs
        );
    }

    /**
     * List all available agents.
     */
    public List<AgentInfo> listAgents() throws Exception {
        String responseBody = doGet("/api/agents");
        JsonNode json = mapper.readTree(responseBody);
        List<AgentInfo> agents = new ArrayList<>();
        if (json.isArray()) {
            for (JsonNode node : json) {
                agents.add(new AgentInfo(
                        node.path("personaId").asText(""),
                        node.path("agentId").asText(""),
                        node.path("name").asText(""),
                        node.path("displayName").asText(""),
                        node.path("description").asText(""),
                        node.path("type").asText("")
                ));
            }
        }
        return agents;
    }

    /**
     * List all active sessions.
     */
    public List<SessionInfo> listSessions() throws Exception {
        String responseBody = doGet("/api/sessions");
        JsonNode json = mapper.readTree(responseBody);
        List<SessionInfo> sessions = new ArrayList<>();
        if (json.isArray()) {
            for (JsonNode node : json) {
                sessions.add(new SessionInfo(
                        node.path("sessionId").asText(""),
                        node.path("agentId").asText(""),
                        node.path("personaId").asText(""),
                        node.path("startedAt").asText(null),
                        node.path("historySize").asInt(0)
                ));
            }
        }
        return sessions;
    }

    /**
     * Get server status.
     */
    public StatusInfo getStatus() throws Exception {
        String responseBody = doGet("/api/status");
        JsonNode json = mapper.readTree(responseBody);
        return new StatusInfo(
                json.path("status").asText("UNKNOWN"),
                json.path("agentCount").asInt(0),
                json.path("activeSessionCount").asInt(0),
                json.path("uptime").asText("unknown")
        );
    }

    /**
     * Delete a session by ID.
     */
    public void deleteSession(String sessionId) throws Exception {
        String url = baseUrl + "/api/sessions/" + sessionId;
        if (debugMode) {
            System.err.println("[DEBUG] DELETE " + url);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (debugMode) {
            System.err.println("[DEBUG] Response (" + response.statusCode() + "): " + response.body());
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    // --- Internal helpers ---

    private String doGet(String path) throws Exception {
        String url = baseUrl + path;
        if (debugMode) {
            System.err.println("[DEBUG] GET " + url);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json; charset=utf-8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (debugMode) {
            System.err.println("[DEBUG] Response (" + response.statusCode() + "): " + response.body());
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private String parseErrorMessage(int statusCode, String body) {
        try {
            JsonNode json = mapper.readTree(body);
            String message = json.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
            String error = json.path("error").asText(null);
            if (error != null && !error.isBlank()) {
                return error;
            }
        } catch (Exception ignored) {}
        return "HTTP " + statusCode + ": " + body;
    }
}
