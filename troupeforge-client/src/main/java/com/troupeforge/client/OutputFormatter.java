package com.troupeforge.client;

import java.util.List;

/**
 * Handles all formatted/colored terminal output using ANSI escape codes.
 * Auto-detects ANSI support and falls back to plain text on unsupported terminals.
 */
public class OutputFormatter {

    // Instance fields - empty strings when colors are disabled
    public final String RESET;
    public final String BOLD;
    public final String DIM;
    public final String RED;
    public final String GREEN;
    public final String YELLOW;
    public final String BLUE;
    public final String CYAN;
    public final String WHITE;
    public final String GRAY;

    private final boolean colorsEnabled;
    private final boolean sanitizeUnicode;

    public OutputFormatter() {
        this(detectAnsiSupport());
    }

    public OutputFormatter(boolean colorsEnabled) {
        this.colorsEnabled = colorsEnabled;
        // On Windows, sanitize Unicode by default since consoles often can't handle it
        this.sanitizeUnicode = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (colorsEnabled) {
            RESET  = "\u001B[0m";
            BOLD   = "\u001B[1m";
            DIM    = "\u001B[2m";
            RED    = "\u001B[31m";
            GREEN  = "\u001B[32m";
            YELLOW = "\u001B[33m";
            BLUE   = "\u001B[34m";
            CYAN   = "\u001B[36m";
            WHITE  = "\u001B[37m";
            GRAY   = "\u001B[90m";
        } else {
            RESET = BOLD = DIM = RED = GREEN = YELLOW = BLUE = CYAN = WHITE = GRAY = "";
        }
    }

    public boolean isColorsEnabled() {
        return colorsEnabled;
    }

    /**
     * Detects whether the current terminal supports ANSI escape codes.
     */
    static boolean detectAnsiSupport() {
        // Force color via env var
        String forceColor = System.getenv("FORCE_COLOR");
        if ("1".equals(forceColor) || "true".equalsIgnoreCase(forceColor)) {
            return true;
        }
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null) {
            return false;
        }

        // No console means piped/redirected - no colors
        if (System.console() == null) {
            return false;
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            // Unix/Mac terminals almost always support ANSI
            return true;
        }

        // On Windows, check for modern terminals that support ANSI
        // Windows Terminal sets WT_SESSION
        if (System.getenv("WT_SESSION") != null) {
            return true;
        }
        // ConEmu/Cmder
        if (System.getenv("ConEmuPID") != null) {
            return true;
        }
        // ANSICON
        if (System.getenv("ANSICON") != null) {
            return true;
        }
        // TERM is set (e.g., Git Bash, MSYS2, Cygwin)
        String term = System.getenv("TERM");
        if (term != null && !term.isEmpty()) {
            return true;
        }
        // IDEA / VS Code integrated terminals
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null && !termProgram.isEmpty()) {
            return true;
        }

        // Windows 10 build 16257+ supports VT via SetConsoleMode, but
        // legacy PowerShell (5.x) and cmd.exe don't enable it by default.
        // Try to enable it via registry check.
        try {
            Process p = new ProcessBuilder("cmd", "/c",
                    "reg query HKCU\\Console /v VirtualTerminalLevel 2>nul")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (output.contains("0x1")) {
                return true;
            }
        } catch (Exception ignored) {}

        // Default: no ANSI on legacy Windows terminals
        return false;
    }

    /**
     * Returns the colored prompt: "[personaId] You> "
     */
    public String agentPrompt(String personaId) {
        return CYAN + "[" + personaId + "]" + RESET + " You" + BOLD + "> " + RESET;
    }

    /**
     * Returns the colored agent response line.
     */
    public String agentResponse(String personaId, String response) {
        return "\n" + BOLD + GREEN + personaId + "> " + RESET + sanitize(response) + "\n";
    }

    /**
     * Returns a red error message.
     */
    public String error(String message) {
        return RED + "Error: " + message + RESET;
    }

    /**
     * Returns a dim/gray info message.
     */
    public String info(String message) {
        return GRAY + message + RESET;
    }

    /**
     * Returns a green success message.
     */
    public String success(String message) {
        return GREEN + message + RESET;
    }

    /**
     * Returns inference breakdown with per-persona lines and a total.
     */
    public String responseMetrics(long latencyMs, ApiClient.TokenUsage usage,
                                   List<ApiClient.InferenceSummary> inferences) {
        if (inferences != null && !inferences.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(DIM);
            int totalIn = 0, totalOut = 0;
            for (int i = 0; i < inferences.size(); i++) {
                var inf = inferences.get(i);
                totalIn += inf.inputTokens();
                totalOut += inf.outputTokens();
                sb.append(String.format("  [%d] %s: (%.1fs, %d tokens (%d in / %d out), %s)%n",
                        i + 1, inf.personaId(),
                        inf.latencyMs() / 1000.0, inf.totalTokens(),
                        inf.inputTokens(), inf.outputTokens(),
                        inf.model() != null ? inf.model() : "unknown"));
            }
            int totalTokens = totalIn + totalOut;
            sb.append(String.format("  [Total] (%.1fs, %d tokens (%d in / %d out))",
                    latencyMs / 1000.0, totalTokens, totalIn, totalOut));
            sb.append(RESET);
            return sb.toString();
        }

        // Fallback: no inference breakdown
        String latency = String.format("%.1fs", latencyMs / 1000.0);
        String tokens = "";
        if (usage != null) {
            tokens = ", " + usage.totalTokens() + " tokens"
                    + " (" + usage.inputTokens() + " in / " + usage.outputTokens() + " out)";
        }
        return DIM + "(" + latency + tokens + ")" + RESET;
    }


    /**
     * Returns a formatted table of agents.
     */
    public String agentTable(List<ApiClient.AgentInfo> agents) {
        if (agents.isEmpty()) {
            return info("No agents found.");
        }

        // Calculate column widths
        int idWidth = "ID".length();
        int nameWidth = "NAME".length();
        int descWidth = "DESCRIPTION".length();

        for (ApiClient.AgentInfo a : agents) {
            idWidth = Math.max(idWidth, a.personaId().length());
            String displayName = a.displayName().isEmpty() ? a.name() : a.displayName();
            nameWidth = Math.max(nameWidth, displayName.length());
            descWidth = Math.max(descWidth, Math.min(a.description().length(), 60));
        }

        // Cap description width
        descWidth = Math.min(descWidth, 60);

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(BOLD);
        sb.append(String.format("  %-" + idWidth + "s  %-" + nameWidth + "s  %-s",
                "ID", "NAME", "DESCRIPTION"));
        sb.append(RESET).append("\n");

        sb.append(DIM);
        sb.append("  " + "-".repeat(idWidth) + "  " + "-".repeat(nameWidth) + "  " + "-".repeat(descWidth));
        sb.append(RESET).append("\n");

        for (ApiClient.AgentInfo a : agents) {
            String displayName = a.displayName().isEmpty() ? a.name() : a.displayName();
            String desc = a.description();
            if (desc.length() > 60) {
                desc = desc.substring(0, 57) + "...";
            }
            sb.append(String.format("  " + CYAN + BOLD + "%-" + idWidth + "s" + RESET
                            + "  %-" + nameWidth + "s  " + DIM + "%s" + RESET,
                    a.personaId(), displayName, desc));
            sb.append("\n");
        }

        sb.append("\n").append(info("  " + agents.size() + " agent(s). Use " + CYAN + "/agent <ID>" + RESET + DIM + " to switch."));
        return sb.toString();
    }

    /**
     * Returns a formatted table of sessions.
     */
    public String sessionTable(List<ApiClient.SessionInfo> sessions) {
        if (sessions.isEmpty()) {
            return info("No active sessions.");
        }

        int idWidth = "SESSION ID".length();
        int personaWidth = "PERSONA".length();
        int historyWidth = "MESSAGES".length();

        for (ApiClient.SessionInfo s : sessions) {
            idWidth = Math.max(idWidth, s.sessionId().length());
            personaWidth = Math.max(personaWidth, s.personaId().length());
        }

        // Cap session ID width for readability
        int displayIdWidth = Math.min(idWidth, 36);

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(BOLD);
        sb.append(String.format("  %-" + displayIdWidth + "s  %-" + personaWidth + "s  %-" + historyWidth + "s  %s",
                "SESSION ID", "PERSONA", "MESSAGES", "STARTED"));
        sb.append(RESET).append("\n");

        sb.append(DIM);
        sb.append("  " + "-".repeat(displayIdWidth) + "  " + "-".repeat(personaWidth) + "  "
                + "-".repeat(historyWidth) + "  " + "-".repeat(19));
        sb.append(RESET).append("\n");

        for (ApiClient.SessionInfo s : sessions) {
            String displayId = s.sessionId();
            if (displayId.length() > displayIdWidth) {
                displayId = displayId.substring(0, displayIdWidth - 3) + "...";
            }
            String started = s.startedAt() != null ? s.startedAt() : "n/a";
            if (started.length() > 19) {
                started = started.substring(0, 19);
            }

            sb.append(String.format("  " + CYAN + "%-" + displayIdWidth + "s" + RESET
                            + "  %-" + personaWidth + "s  %-" + historyWidth + "d  %s",
                    displayId, s.personaId(), s.historySize(), started));
            sb.append("\n");
        }

        sb.append("\n").append(info("  " + sessions.size() + " session(s). Use /session resume <id> to resume."));
        return sb.toString();
    }

    /**
     * Returns a formatted status display.
     */
    public String statusDisplay(ApiClient.StatusInfo status) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(BOLD + "  Server Status" + RESET).append("\n");
        sb.append(DIM + "  " + "-".repeat(30) + RESET).append("\n");

        String statusColor = "UP".equalsIgnoreCase(status.status()) ? GREEN : RED;
        sb.append("  Status:           ").append(statusColor + BOLD + status.status() + RESET).append("\n");
        sb.append("  Agents:           ").append(status.agentCount()).append("\n");
        sb.append("  Active Sessions:  ").append(status.activeSessionCount()).append("\n");
        sb.append("  Uptime:           ").append(status.uptime()).append("\n");

        return sb.toString();
    }

    /**
     * Returns comprehensive help text with all commands.
     */
    public String helpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(BOLD + "  TroupeForge CLI Commands" + RESET).append("\n");
        sb.append(DIM + "  " + "-".repeat(40) + RESET).append("\n\n");

        sb.append(BOLD + "  Chat" + RESET).append("\n");
        sb.append("    Type any message to chat with the current agent.\n\n");

        sb.append(BOLD + "  Agent Commands" + RESET).append("\n");
        sb.append("    " + CYAN + "/agents" + RESET + "              List all available agents\n");
        sb.append("    " + CYAN + "/agent <name>" + RESET + "       Switch to a different agent\n\n");

        sb.append(BOLD + "  Session Commands" + RESET).append("\n");
        sb.append("    " + CYAN + "/session new" + RESET + "        Start a new session (clear current)\n");
        sb.append("    " + CYAN + "/session list" + RESET + "       List all active sessions\n");
        sb.append("    " + CYAN + "/session last" + RESET + "       Resume the last session for current agent\n");
        sb.append("    " + CYAN + "/session resume <id>" + RESET + " Resume a previous session\n\n");

        sb.append(BOLD + "  Server" + RESET).append("\n");
        sb.append("    " + CYAN + "/status" + RESET + "             Show server status\n\n");

        sb.append(BOLD + "  Diagnostics" + RESET).append("\n");
        sb.append("    " + CYAN + "/cost" + RESET + "               Show accumulated token usage\n");
        sb.append("    " + CYAN + "/debug [on|off]" + RESET + "     Toggle debug mode (show raw JSON)\n");
        sb.append("    " + CYAN + "/history" + RESET + "            Show recent conversation exchanges\n\n");

        sb.append(BOLD + "  General" + RESET).append("\n");
        sb.append("    " + CYAN + "/help" + RESET + "               Show this help text\n");
        sb.append("    " + CYAN + "/clear" + RESET + "              Clear the terminal screen\n");
        sb.append("    " + CYAN + "/exit" + RESET + "               Exit the CLI\n");

        return sb.toString();
    }

    /**
     * Returns the welcome banner.
     */
    public String welcomeBanner(String agent, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(BOLD + "  TroupeForge CLI" + RESET).append("\n");
        sb.append(DIM + "  " + "-".repeat(40) + RESET).append("\n");
        sb.append("  Agent:   " + CYAN + BOLD + agent + RESET).append("\n");
        sb.append("  Server:  " + DIM + url + RESET).append("\n");
        sb.append("  Type " + CYAN + "/help" + RESET + " for available commands.\n");
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Returns a goodbye message.
     */
    public String goodbye() {
        return "\n" + DIM + "Goodbye!" + RESET + "\n";
    }

    /**
     * Sanitizes text for Windows console output by replacing Unicode characters
     * that cause encoding issues (em dashes, smart quotes, emojis, etc.) with
     * ASCII equivalents. No-op on non-Windows systems.
     */
    public String sanitize(String text) {
        if (!sanitizeUnicode || text == null) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            switch (cp) {
                case 0x2014 -> sb.append("--");   // em dash
                case 0x2013 -> sb.append("-");     // en dash
                case 0x2018, 0x2019 -> sb.append("'");  // smart single quotes
                case 0x201C, 0x201D -> sb.append("\""); // smart double quotes
                case 0x2026 -> sb.append("...");   // ellipsis
                case 0x2022 -> sb.append("*");     // bullet
                case 0x00A0 -> sb.append(" ");     // non-breaking space
                default -> {
                    if (cp > 0xFFFF) {
                        // Supplementary plane (emojis etc.) — skip them
                    } else if (cp > 0x7E && cp < 0xA0) {
                        // Control characters — skip
                    } else {
                        sb.appendCodePoint(cp);
                    }
                }
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
