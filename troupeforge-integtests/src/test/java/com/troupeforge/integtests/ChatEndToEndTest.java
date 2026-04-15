package com.troupeforge.integtests;

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

@DisplayName("Chat End-to-End Integration Tests")
class ChatEndToEndTest {

    private static final String BASE_URL =
            System.getProperty("integtest.base-url", "http://localhost:8080");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    record ChatRequest(String personaId, String message, String sessionId) {}
    record ChatResponse(String requestId, String sessionId, String personaId, String response) {}

    private HttpResponse<String> chatRaw(String personaId, String message, String sessionId) throws Exception {
        ChatRequest req = new ChatRequest(personaId, message, sessionId);
        String body = MAPPER.writeValueAsString(req);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(REQUEST_TIMEOUT)
                .build();

        return HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private ChatResponse chat(String personaId, String message, String sessionId) throws Exception {
        HttpResponse<String> httpResponse = chatRaw(personaId, message, sessionId);
        assertEquals(200, httpResponse.statusCode());
        ChatResponse resp = MAPPER.readValue(httpResponse.body(), ChatResponse.class);

        assertNotNull(resp.requestId());
        assertFalse(resp.requestId().isBlank());
        assertNotNull(resp.sessionId());
        assertFalse(resp.sessionId().isBlank());
        assertNotNull(resp.personaId());
        assertNotNull(resp.response());
        assertFalse(resp.response().isBlank());

        assertTrue(resp.requestId().matches("[0-9a-f\\-]{36}"));
        assertTrue(resp.sessionId().matches("[0-9a-f\\-]{36}"));

        return resp;
    }

    // ---- Persona behavior tests ----

    @Test
    @DisplayName("Simon echo persona repeats user message")
    void simonEchoesMessage() throws Exception {
        ChatResponse response = chat("simon", "Hello world!", null);

        assertEquals("simon", response.personaId());

        String text = response.response();
        Pattern echoPattern = Pattern.compile("(?i)(simon says|hello|world)");
        assertTrue(echoPattern.matcher(text).find());
    }

    @Test
    @DisplayName("Bond persona responds in character")
    void bondRespondsInCharacter() throws Exception {
        ChatResponse response = chat("bond", "Good evening, who are you?", null);

        assertEquals("bond", response.personaId());

        String text = response.response();
        Pattern bondPattern = Pattern.compile("(?i)(bond[.,!]?\\s*james\\s*bond|james\\s*bond|007|bond)");
        assertTrue(bondPattern.matcher(text).find());
    }

    // ---- Session continuity ----

    @Test
    @DisplayName("Session continuity — second message in same session remembers context")
    void sessionContinuity() throws Exception {
        ChatResponse first = chat("bond", "My name is Vesper", null);
        String sessionId = first.sessionId();

        assertEquals("bond", first.personaId());

        ChatResponse second = chat("bond", "What is my name?", sessionId);

        assertEquals(sessionId, second.sessionId());
        assertEquals("bond", second.personaId());

        Pattern vesperPattern = Pattern.compile("(?i)vesper");
        assertTrue(vesperPattern.matcher(second.response()).find());
    }

    // ---- Different personas ----

    @Test
    @DisplayName("Different personas produce distinctly different response styles")
    void differentPersonasProduceDifferentStyles() throws Exception {
        String message = "Tell me about yourself in one sentence";

        ChatResponse simonResp = chat("simon", message, null);
        ChatResponse bondResp = chat("bond", message, null);

        assertEquals("simon", simonResp.personaId());
        assertEquals("bond", bondResp.personaId());

        assertNotEquals(simonResp.response(), bondResp.response());

        Pattern simonPattern = Pattern.compile("(?i)(simon says|tell me|yourself)");
        assertTrue(simonPattern.matcher(simonResp.response()).find());

        Pattern bondPattern = Pattern.compile("(?i)(bond|james|007|spy|agent|mi6)");
        assertTrue(bondPattern.matcher(bondResp.response()).find());
    }

    // ---- File operation tests ----

    @Test
    @DisplayName("Analyst lists files and returns recognizable project files")
    void analystListsFiles() throws Exception {
        ChatResponse response = chat("analyst",
                "List the files in the current directory and tell me what you see", null);

        assertEquals("analyst", response.personaId());

        String text = response.response();

        Pattern projectFilePattern = Pattern.compile(
                "(?i)(build\\.gradle|gradlew|settings\\.gradle|troupeforge-|gradle|\\.kts)");
        assertTrue(projectFilePattern.matcher(text).find());

        Pattern anyFilePattern = Pattern.compile("(?i)(build|gradle|settings|troupeforge|src)");
        long fileRefs = anyFilePattern.matcher(text).results().count();
        assertTrue(fileRefs >= 2);
    }

    @Test
    @DisplayName("Analyst reads file head and returns actual file content")
    void analystReadsFileHead() throws Exception {
        ChatResponse response = chat("analyst",
                "Read the first 5 lines of settings.gradle.kts and show me the content", null);

        assertEquals("analyst", response.personaId());

        String text = response.response();

        Pattern settingsPattern = Pattern.compile(
                "(?i)(rootProject|include|settings|pluginManagement|troupeforge)");
        assertTrue(settingsPattern.matcher(text).find());
    }

    @Test
    @DisplayName("Analyst reads a specific file and returns its content")
    void analystReadsFile() throws Exception {
        ChatResponse response = chat("analyst",
                "Read the file build.gradle.kts and show me its full content", null);

        assertEquals("analyst", response.personaId());

        String text = response.response();

        Pattern buildPattern = Pattern.compile("(?i)(plugins|subprojects|allprojects|dependencies|gradle)");
        assertTrue(buildPattern.matcher(text).find());
    }

    // ---- Agent delegation tests ----

    @Test
    @DisplayName("Nick delegates to analyst and returns structured findings")
    void nickDelegatesToAnalyst() throws Exception {
        ChatResponse response = chat("nick",
                "Delegate to the analyst persona to read the first 5 lines of settings.gradle.kts. " +
                "Use delegate_to_agent with personaId 'analyst'. Report what they found.", null);

        assertEquals("nick", response.personaId());

        String text = response.response();

        // Nick's response should contain file content from the analyst delegation
        Pattern contentPattern = Pattern.compile(
                "(?i)(rootProject|include|settings|pluginManagement|troupeforge|gradle)");
        assertTrue(contentPattern.matcher(text).find());
    }

    // ---- Agent handover tests ----

    @Test
    @DisplayName("Linda dispatcher hands over to Bond — response is from Bond persona")
    void lindaHandsOverToBond() throws Exception {
        ChatResponse response = chat("linda",
                "I want to talk to the spy character. Please connect me with Bond.", null);

        // Handover: the responding persona should be bond, not linda
        assertEquals("bond", response.personaId());

        String text = response.response();

        // Linda should handover to Bond; the response content should be Bond-like
        Pattern bondPattern = Pattern.compile("(?i)(bond|james|007|spy|agent|secret|shaken|martini)");
        assertTrue(bondPattern.matcher(text).find());
    }

    @Test
    @DisplayName("Linda dispatcher routes greeting request — handover produces a response")
    void lindaRoutesGreeting() throws Exception {
        ChatResponse response = chat("linda",
                "I need someone to greet me warmly. Who can do that?", null);

        // Linda may handover to a greeter — personaId could be linda or a greeter persona
        assertNotNull(response.personaId());

        String text = response.response();

        assertFalse(text.isBlank());
        assertTrue(text.length() > 10);
    }

    // ---- Error handling ----

    @Test
    @DisplayName("Invalid persona returns HTTP 400 with error details")
    void invalidPersonaReturnsError() throws Exception {
        HttpResponse<String> response = chatRaw("nonexistent-persona", "Hello", null);

        assertEquals(400, response.statusCode());

        assertNotNull(response.body());
        assertFalse(response.body().isBlank());

        Pattern errorPattern = Pattern.compile("(?i)(error|not found|no profile|nonexistent)");
        assertTrue(errorPattern.matcher(response.body()).find());
    }

    @Test
    @DisplayName("Empty message is rejected")
    void emptyMessageIsRejected() throws Exception {
        HttpResponse<String> response = chatRaw("simon", " ", null);
        assertTrue(response.statusCode() >= 400);
    }

    @Test
    @DisplayName("Null message is rejected")
    void nullMessageIsRejected() throws Exception {
        HttpResponse<String> response = chatRaw("simon", null, null);
        assertTrue(response.statusCode() >= 400);
    }
}
