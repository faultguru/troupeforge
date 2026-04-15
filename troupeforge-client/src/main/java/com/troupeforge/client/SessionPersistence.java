package com.troupeforge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists session state (last session per agent) to disk.
 * Sessions are stored in ~/.troupeforge/sessions.json
 */
public class SessionPersistence {

    private static final String DIR_NAME = ".troupeforge";
    private static final String FILE_NAME = "sessions.json";

    private final Path sessionsFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode data;

    public SessionPersistence() {
        Path home = Path.of(System.getProperty("user.home"));
        Path dir = home.resolve(DIR_NAME);
        this.sessionsFile = dir.resolve(FILE_NAME);
        load();
    }

    /**
     * Save the last session ID for a given agent.
     */
    public void saveSession(String personaId, String sessionId) {
        if (personaId == null || sessionId == null) return;
        data.put(personaId, sessionId);
        persist();
    }

    /**
     * Get the last session ID for a given agent, or null.
     */
    public String getLastSession(String personaId) {
        if (personaId == null || !data.has(personaId)) return null;
        JsonNode node = data.get(personaId);
        return node.isTextual() ? node.asText() : null;
    }

    /**
     * Remove a session entry.
     */
    public void removeSession(String personaId) {
        if (personaId == null) return;
        data.remove(personaId);
        persist();
    }

    /**
     * Clear all saved sessions.
     */
    public void clearAll() {
        data = mapper.createObjectNode();
        persist();
    }

    private void load() {
        try {
            if (Files.exists(sessionsFile)) {
                JsonNode root = mapper.readTree(sessionsFile.toFile());
                if (root.isObject()) {
                    data = (ObjectNode) root;
                    return;
                }
            }
        } catch (IOException ignored) {}
        data = mapper.createObjectNode();
    }

    private void persist() {
        try {
            Files.createDirectories(sessionsFile.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(sessionsFile.toFile(), data);
        } catch (IOException e) {
            // Silent failure - persistence is best-effort
        }
    }
}
