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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tool Execution End-to-End Integration Tests")
class ToolExecutionEndToEndTest {

    private static final String BASE_URL =
            System.getProperty("integtest.base-url", "http://localhost:8080");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // ---- Calculator Tool ----

    @Test
    @DisplayName("Calculator agent performs arithmetic correctly")
    void calculatorPerformsArithmetic() throws Exception {
        String response = chat("calc", "What is 15 multiplied by 7? Use the calculator tool.");

        // Should contain 105
        Pattern resultPattern = Pattern.compile("105");
        assertTrue(resultPattern.matcher(response).find(),
                "Calculator should return 105 for 15*7. Got: " + response);
    }

    // ---- Think Tool ----

    @Test
    @DisplayName("Agent uses think tool for reasoning without it affecting response")
    void thinkToolDoesNotAffectResponse() throws Exception {
        String response = chat("calc",
                "Think about what 2+2 is using the think tool, then tell me the answer.");

        // Should get a coherent answer, not the think tool's internal output
        assertFalse(response.isBlank());
        Pattern numberPattern = Pattern.compile("4");
        assertTrue(numberPattern.matcher(response).find(),
                "Should contain the answer 4. Got: " + response);
    }

    // ---- File Tools ----

    @Test
    @DisplayName("Analyst searches files and finds results")
    void analystSearchesFiles() throws Exception {
        String response = chat("analyst",
                "Search for the word 'TroupeForge' in all .java files in the current directory tree. Use the search_files tool.");

        assertFalse(response.isBlank());
        // Should find matches
        Pattern matchPattern = Pattern.compile("(?i)(found|match|result|troupeforge)");
        assertTrue(matchPattern.matcher(response).find(),
                "Should report search results. Got: " + response);
    }

    // ---- Echo Agent (no tools) ----

    @Test
    @DisplayName("Echo/parrot agent repeats user message exactly")
    void echoParrotRepeatsMessage() throws Exception {
        String testMessage = "The quick brown fox jumps over the lazy dog";
        String response = chat("parrot", testMessage);

        // Parrot should repeat the exact message
        assertTrue(response.toLowerCase().contains(testMessage.toLowerCase()),
                "Parrot should echo back the message. Got: " + response);
    }

    // ---- Delegation Chain ----

    @Test
    @DisplayName("Delegation chain: dispatcher delegates to analyst who uses tools")
    void delegationChainWithToolUse() throws Exception {
        String response = chat("nick",
                "Ask the analyst to list files in the current directory using their tools. " +
                "Use delegate_to_agent with personaId 'analyst'. Report what they found.");

        assertFalse(response.isBlank());
        // Nick's response should contain info from analyst's file listing
        Pattern filePattern = Pattern.compile("(?i)(file|directory|build|gradle|troupeforge|src)");
        assertTrue(filePattern.matcher(response).find(),
                "Nick should relay analyst's file listing results. Got: " + response);
    }

    // ---- Error Recovery ----

    @Test
    @DisplayName("Agent recovers from tool failure and provides response")
    void agentRecoversFromToolFailure() throws Exception {
        // Ask analyst to read a non-existent file — it should handle the error gracefully
        String response = chat("analyst",
                "Try to read a file called 'definitely_does_not_exist_xyz123.txt'. " +
                "If it fails, just tell me it doesn't exist.");

        assertFalse(response.isBlank());
        Pattern errorPattern = Pattern.compile("(?i)(not found|does not exist|error|cannot|couldn't|unable|no such)");
        assertTrue(errorPattern.matcher(response).find(),
                "Agent should gracefully report the file doesn't exist. Got: " + response);
    }

    // ---- Helpers ----

    private String chat(String personaId, String message) throws Exception {
        var body = MAPPER.createObjectNode();
        body.put("personaId", personaId);
        body.put("message", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .timeout(Duration.ofMinutes(2))
                .build();

        HttpResponse<String> httpResponse = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, httpResponse.statusCode(),
                "Chat should succeed. Response: " + httpResponse.body());

        JsonNode json = MAPPER.readTree(httpResponse.body());
        return json.path("response").asText();
    }
}
