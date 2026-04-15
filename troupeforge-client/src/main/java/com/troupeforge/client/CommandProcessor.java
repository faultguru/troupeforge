package com.troupeforge.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Processes slash commands and returns formatted output.
 */
public class CommandProcessor {

    /**
     * Callback interface for commands that need to change REPL state.
     */
    public interface CommandCallback {
        void switchAgent(String personaId);
        void setSessionId(String sessionId);
        void clearSession();
        String getCurrentAgent();
        String getCurrentSessionId();
    }

    private final ApiClient apiClient;
    private final OutputFormatter formatter;
    private final SessionPersistence sessionPersistence;
    private final CommandCallback callback;

    // Accumulated usage tracking
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;
    private int totalRequests = 0;
    private long totalLatencyMs = 0;

    // Conversation history (recent exchanges)
    private final List<String> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 20;

    public CommandProcessor(ApiClient apiClient, OutputFormatter formatter,
                            SessionPersistence sessionPersistence, CommandCallback callback) {
        this.apiClient = apiClient;
        this.formatter = formatter;
        this.sessionPersistence = sessionPersistence;
        this.callback = callback;
    }

    /**
     * Returns true if input starts with '/'.
     */
    public boolean isCommand(String input) {
        return input.startsWith("/");
    }

    /**
     * Returns true if this is an exit command.
     */
    public boolean isExitCommand(String input) {
        String lower = input.toLowerCase().trim();
        return lower.equals("/exit") || lower.equals("/quit");
    }

    /**
     * Process a slash command and return the output to display.
     * Returns null for /clear (caller handles screen clear).
     */
    public String processCommand(String input) {
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();

        // Parse command and args
        String command;
        String args = "";
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            command = trimmed.substring(0, spaceIdx).toLowerCase();
            args = trimmed.substring(spaceIdx + 1).trim();
        } else {
            command = lower;
        }

        return switch (command) {
            case "/help" -> formatter.helpText();
            case "/agents" -> handleAgents();
            case "/agent" -> handleAgentSwitch(args);
            case "/session" -> handleSession(args);
            case "/status" -> handleStatus();
            case "/cost" -> handleCost();
            case "/debug" -> handleDebug(args);
            case "/history" -> handleHistory();
            case "/clear" -> null;
            case "/exit", "/quit" -> null;
            default -> formatter.error("Unknown command: " + command + ". Type /help for available commands.");
        };
    }

    /**
     * Record a chat exchange for history and usage tracking.
     */
    public void recordExchange(String agent, String userMessage, String agentResponse,
                                ApiClient.TokenUsage usage, long latencyMs) {
        totalRequests++;
        totalLatencyMs += latencyMs;
        if (usage != null) {
            totalInputTokens += usage.inputTokens();
            totalOutputTokens += usage.outputTokens();
        }

        // Add to history
        String entry = formatter.DIM + "You: " + formatter.RESET + truncate(userMessage, 80) + "\n"
                + formatter.BOLD + formatter.GREEN + agent + ": " + formatter.RESET
                + truncate(agentResponse, 120);
        conversationHistory.add(entry);
        if (conversationHistory.size() > MAX_HISTORY) {
            conversationHistory.remove(0);
        }
    }

    // --- Command handlers ---

    private String handleAgents() {
        try {
            List<ApiClient.AgentInfo> agents = apiClient.listAgents();
            return formatter.agentTable(agents);
        } catch (Exception e) {
            return formatter.error("Failed to list agents: " + e.getMessage());
        }
    }

    private String handleAgentSwitch(String args) {
        if (args.isEmpty()) {
            return formatter.error("Usage: /agent <persona-id>");
        }
        callback.switchAgent(args);
        return formatter.success("Switched to agent: " + args + " (new session)");
    }

    private String handleSession(String args) {
        if (args.isEmpty()) {
            return formatter.error("Usage: /session new | /session list | /session last | /session resume <id>");
        }

        String subCommand;
        String subArgs = "";
        int spaceIdx = args.indexOf(' ');
        if (spaceIdx > 0) {
            subCommand = args.substring(0, spaceIdx).toLowerCase();
            subArgs = args.substring(spaceIdx + 1).trim();
        } else {
            subCommand = args.toLowerCase();
        }

        return switch (subCommand) {
            case "new" -> {
                callback.clearSession();
                yield formatter.success("Session cleared. Next message will start a new session.");
            }
            case "list" -> {
                try {
                    List<ApiClient.SessionInfo> sessions = apiClient.listSessions();
                    yield formatter.sessionTable(sessions);
                } catch (Exception e) {
                    yield formatter.error("Failed to list sessions: " + e.getMessage());
                }
            }
            case "resume" -> {
                if (subArgs.isEmpty()) {
                    yield formatter.error("Usage: /session resume <session-id>");
                }
                callback.setSessionId(subArgs);
                yield formatter.success("Resumed session: " + subArgs);
            }
            case "last" -> {
                String lastSession = sessionPersistence.getLastSession(callback.getCurrentAgent());
                if (lastSession == null) {
                    yield formatter.info("No saved session for agent: " + callback.getCurrentAgent());
                } else {
                    callback.setSessionId(lastSession);
                    yield formatter.success("Resumed last session: " + lastSession);
                }
            }
            default -> formatter.error("Unknown session command: " + subCommand
                    + ". Use: new, list, last, or resume <id>");
        };
    }

    private String handleStatus() {
        try {
            ApiClient.StatusInfo status = apiClient.getStatus();
            return formatter.statusDisplay(status);
        } catch (Exception e) {
            return formatter.error("Failed to get status: " + e.getMessage());
        }
    }

    private String handleCost() {
        int totalTokens = totalInputTokens + totalOutputTokens;
        // Estimate cost using Sonnet pricing: $3/MTok input, $15/MTok output
        double inputCost = totalInputTokens * 3.0 / 1_000_000;
        double outputCost = totalOutputTokens * 15.0 / 1_000_000;
        double totalCost = inputCost + outputCost;

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(formatter.BOLD + "  Token Usage & Cost Estimate" + formatter.RESET).append("\n");
        sb.append(formatter.DIM + "  " + "-".repeat(35) + formatter.RESET).append("\n");
        sb.append("  Requests:       ").append(totalRequests).append("\n");
        sb.append("  Input tokens:   ").append(String.format("%,d", totalInputTokens)).append("\n");
        sb.append("  Output tokens:  ").append(String.format("%,d", totalOutputTokens)).append("\n");
        sb.append("  Total tokens:   ").append(String.format("%,d", totalTokens)).append("\n");
        if (totalRequests > 0) {
            sb.append("  Avg latency:    ")
                    .append(String.format("%.1fs", (totalLatencyMs / (double) totalRequests) / 1000.0))
                    .append("\n");
        }
        sb.append(formatter.DIM + "  " + "-".repeat(35) + formatter.RESET).append("\n");
        sb.append("  Est. cost:      ").append(formatter.YELLOW)
                .append(String.format("$%.4f", totalCost)).append(formatter.RESET)
                .append(formatter.DIM).append(" (Sonnet pricing)")
                .append(formatter.RESET).append("\n");
        return sb.toString();
    }

    private String handleDebug(String args) {
        if (args.isEmpty()) {
            // Toggle
            boolean newValue = !apiClient.isDebugMode();
            apiClient.setDebugMode(newValue);
            return formatter.info("Debug mode: " + (newValue ? "ON" : "OFF"));
        }
        boolean on = args.equalsIgnoreCase("on") || args.equalsIgnoreCase("true");
        apiClient.setDebugMode(on);
        return formatter.info("Debug mode: " + (on ? "ON" : "OFF"));
    }

    private String handleHistory() {
        if (conversationHistory.isEmpty()) {
            return formatter.info("No conversation history yet.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(formatter.BOLD + "  Conversation History" + formatter.RESET).append("\n");
        sb.append(formatter.DIM + "  " + "-".repeat(40) + formatter.RESET).append("\n\n");

        for (int i = 0; i < conversationHistory.size(); i++) {
            sb.append("  ").append(formatter.DIM + "[" + (i + 1) + "]" + formatter.RESET).append(" ");
            sb.append(conversationHistory.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    // --- Helpers ---

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        // Replace newlines for single-line display
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= maxLen) return oneLine;
        return oneLine.substring(0, maxLen - 3) + "...";
    }
}
