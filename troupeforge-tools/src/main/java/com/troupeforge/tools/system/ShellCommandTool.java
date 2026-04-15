package com.troupeforge.tools.system;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands with timeout and output capture.
 */
public class ShellCommandTool implements Tool {

    public static final String NAME = "shell_command";

    private static final int MAX_OUTPUT_CHARS = 10000;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public record Request(
        @ToolParam(description = "The command to execute")
        String command,
        @ToolParam(description = "Command arguments", required = false)
        List<String> args,
        @ToolParam(description = "Working directory (default: agent working directory)", required = false)
        String workingDirectory,
        @ToolParam(description = "Timeout in seconds (default 30, max 120)", required = false)
        Integer timeoutSeconds
    ) {}

    public record Response(int exitCode, String stdout, String stderr, boolean timedOut, long durationMs) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Execute shell commands with timeout and output capture"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        Path workDir = context.workingDirectory();

        int timeout = Math.min(
                req.timeoutSeconds() != null ? req.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS,
                MAX_TIMEOUT_SECONDS);

        Path execDir = workDir;
        if (req.workingDirectory() != null) {
            execDir = workDir.resolve(req.workingDirectory()).normalize();
            if (!execDir.normalize().startsWith(workDir.normalize()) || !Files.isDirectory(execDir)) {
                throw new IllegalArgumentException("Invalid working directory: " + req.workingDirectory());
            }
        }

        List<String> commandList = new ArrayList<>();
        commandList.add(req.command());
        if (req.args() != null) {
            commandList.addAll(req.args());
        }

        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.directory(execDir.toFile());

        long startTime = System.currentTimeMillis();

        try {
            Process process = pb.start();

            StringBuilder stdoutBuilder = new StringBuilder();
            StringBuilder stderrBuilder = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stdoutBuilder) {
                            if (stdoutBuilder.length() > 0) stdoutBuilder.append('\n');
                            stdoutBuilder.append(line);
                        }
                    }
                } catch (IOException e) {
                    // ignore read errors
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stderrBuilder) {
                            if (stderrBuilder.length() > 0) stderrBuilder.append('\n');
                            stderrBuilder.append(line);
                        }
                    }
                } catch (IOException e) {
                    // ignore read errors
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            long durationMs = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                stdoutThread.join(1000);
                stderrThread.join(1000);
                return new Response(-1, truncate(stdoutBuilder.toString()), truncate(stderrBuilder.toString()), true, durationMs);
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            return new Response(
                    process.exitValue(),
                    truncate(stdoutBuilder.toString()),
                    truncate(stderrBuilder.toString()),
                    false,
                    durationMs);

        } catch (IOException e) {
            throw new RuntimeException("Failed to execute command: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Command execution interrupted", e);
        }
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_OUTPUT_CHARS) return s;
        return s.substring(0, MAX_OUTPUT_CHARS) + "\n... [truncated]";
    }
}
