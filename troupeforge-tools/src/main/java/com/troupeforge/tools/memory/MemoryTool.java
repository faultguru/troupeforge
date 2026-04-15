package com.troupeforge.tools.memory;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped key-value memory store.
 * Each session gets its own isolated namespace.
 */
public class MemoryTool implements Tool {

    public static final String NAME = "memory";

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sessionStores = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS = 1000;

    private ConcurrentHashMap<String, String> storeFor(ToolContext context) {
        return sessionStores.computeIfAbsent(context.agentSessionId().value(), k -> new ConcurrentHashMap<>());
    }

    /**
     * Remove memory for a specific session. Should be called when sessions terminate.
     */
    public void cleanupSession(String sessionId) {
        sessionStores.remove(sessionId);
    }

    /**
     * Returns the number of active session stores.
     */
    public int activeSessionCount() {
        return sessionStores.size();
    }

    public record Request(
        @ToolParam(description = "Action: set, get, list, or delete")
        String action,
        @ToolParam(description = "The key to store/retrieve/delete", required = false)
        String key,
        @ToolParam(description = "The value to store (for set action)", required = false)
        String value
    ) {}

    public record Response(boolean success, String value, List<String> keys, String error) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Store and retrieve key-value pairs for session memory"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        String action = req.action() != null ? req.action().toLowerCase() : "";
        var store = storeFor(context);

        return switch (action) {
            case "set" -> {
                if (req.key() == null || req.value() == null) {
                    yield new Response(false, null, null, "Both key and value are required for set action");
                }
                store.put(req.key(), req.value());
                yield new Response(true, null, null, null);
            }
            case "get" -> {
                if (req.key() == null) {
                    yield new Response(false, null, null, "Key is required for get action");
                }
                String val = store.get(req.key());
                if (val == null) {
                    yield new Response(false, null, null, "Key not found: " + req.key());
                }
                yield new Response(true, val, null, null);
            }
            case "list" -> {
                List<String> keys = new ArrayList<>(store.keySet());
                yield new Response(true, null, keys, null);
            }
            case "delete" -> {
                if (req.key() == null) {
                    yield new Response(false, null, null, "Key is required for delete action");
                }
                store.remove(req.key());
                yield new Response(true, null, null, null);
            }
            default -> new Response(false, null, null, "Unknown action: " + action + ". Use set, get, list, or delete.");
        };
    }
}
