package com.troupeforge.client;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * TroupeForge CLI - an agent-based chat client.
 */
public class TroupeForgeClient {

    private final String baseUrl;
    private final ApiClient apiClient;
    private final OutputFormatter formatter;
    private final CommandProcessor commandProcessor;
    private final SessionPersistence sessionPersistence;
    private final boolean streamingMode;

    private String personaId;
    private String sessionId;

    public TroupeForgeClient(String baseUrl, String initialAgent) {
        this(baseUrl, initialAgent, false, null);
    }

    public TroupeForgeClient(String baseUrl, String initialAgent, boolean streamingMode, Boolean colorOverride) {
        this.baseUrl = baseUrl;
        this.apiClient = new ApiClient(baseUrl);
        this.formatter = colorOverride != null
                ? new OutputFormatter(colorOverride)
                : new OutputFormatter(); // auto-detect
        this.sessionPersistence = new SessionPersistence();
        this.personaId = initialAgent;
        this.streamingMode = streamingMode;

        this.commandProcessor = new CommandProcessor(apiClient, formatter, sessionPersistence, new CommandProcessor.CommandCallback() {
            @Override
            public void switchAgent(String newPersonaId) {
                personaId = newPersonaId;
                sessionId = null;
            }

            @Override
            public void setSessionId(String newSessionId) {
                sessionId = newSessionId;
            }

            @Override
            public void clearSession() {
                sessionId = null;
            }

            @Override
            public String getCurrentAgent() {
                return personaId;
            }

            @Override
            public String getCurrentSessionId() {
                return sessionId;
            }
        });
    }

    /**
     * Run the REPL loop.
     */
    public void run() {
        System.out.print(formatter.welcomeBanner(personaId, baseUrl));

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print(formatter.agentPrompt(personaId));
                System.out.flush();

                if (!scanner.hasNextLine()) break;

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                // Handle commands
                if (commandProcessor.isCommand(input)) {
                    if (commandProcessor.isExitCommand(input)) {
                        break;
                    }

                    if (input.trim().equalsIgnoreCase("/clear")) {
                        if (formatter.isColorsEnabled()) {
                            System.out.print("\033[H\033[2J");
                            System.out.flush();
                        }
                        continue;
                    }

                    String output = commandProcessor.processCommand(input);
                    if (output != null) {
                        System.out.println(output);
                    }
                    continue;
                }

                // Chat message
                try {
                    ApiClient.ChatResult result;
                    if (streamingMode) {
                        // Print agent name prefix, then stream deltas inline
                        System.out.print("\n" + formatter.BOLD + formatter.GREEN
                                + personaId + "> " + formatter.RESET);
                        System.out.flush();
                        result = apiClient.sendStreaming(personaId, input, sessionId, delta -> {
                            System.out.print(formatter.sanitize(delta));
                            System.out.flush();
                        });
                        System.out.println();
                    } else {
                        result = apiClient.send(personaId, input, sessionId);
                        System.out.print(formatter.agentResponse(result.personaId(), result.response()));
                    }

                    // Update state from response
                    sessionId = result.sessionId();
                    if (sessionId != null) {
                        sessionPersistence.saveSession(personaId, sessionId);
                    }
                    if (result.personaId() != null && !result.personaId().equals(personaId)) {
                        // Agent handover occurred
                        personaId = result.personaId();
                    }

                    // Display metrics
                    System.out.println(formatter.responseMetrics(result.latencyMs(), result.tokenUsage(), result.inferences()));

                    // Track for /history and /cost
                    commandProcessor.recordExchange(
                            result.personaId(), input, result.response(),
                            result.tokenUsage(), result.latencyMs());

                } catch (Exception e) {
                    System.out.println(formatter.error(e.getMessage()));
                }
            }
        }

        System.out.print(formatter.goodbye());
    }

    public static void main(String[] args) {
        String url = "http://localhost:8080";
        String agent = "linda";
        boolean stream = false;
        Boolean colorOverride = null; // null = auto-detect

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url", "-u" -> { if (i + 1 < args.length) url = args[++i]; }
                case "--agent", "-a" -> { if (i + 1 < args.length) agent = args[++i]; }
                case "--stream", "-s" -> stream = true;
                case "--color" -> colorOverride = true;
                case "--no-color" -> colorOverride = false;
                case "--help", "-h" -> {
                    System.out.println("Usage: troupeforge-client [OPTIONS]");
                    System.out.println("  --url, -u URL     Server URL (default: http://localhost:8080)");
                    System.out.println("  --agent, -a NAME  Persona ID (default: linda)");
                    System.out.println("  --stream, -s      Enable streaming mode");
                    System.out.println("  --color           Force colored output");
                    System.out.println("  --no-color        Disable colored output");
                    System.out.println();
                    System.out.println("Environment variables:");
                    System.out.println("  FORCE_COLOR=1     Force colored output");
                    System.out.println("  NO_COLOR          Disable colored output (any value)");
                    return;
                }
            }
        }

        // Ensure UTF-8 output on Windows consoles
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                // Set console code page to UTF-8 — must use inheritIO so it affects THIS console
                new ProcessBuilder("cmd", "/c", "chcp", "65001")
                        .inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }

        // Recreate System.out/err from raw file descriptors with explicit UTF-8 encoding.
        // This bypasses any stale encoding from JVM startup.
        System.setOut(new PrintStream(new BufferedOutputStream(
                new FileOutputStream(FileDescriptor.out)), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new BufferedOutputStream(
                new FileOutputStream(FileDescriptor.err)), true, StandardCharsets.UTF_8));

        TroupeForgeClient client = new TroupeForgeClient(url, agent, stream, colorOverride);
        client.run();
    }
}
