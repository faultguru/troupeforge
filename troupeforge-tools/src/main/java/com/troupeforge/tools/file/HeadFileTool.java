package com.troupeforge.tools.file;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the first N lines from one or more files.
 * Accepts explicit file paths and/or a regex pattern to match filenames.
 */
public class HeadFileTool implements Tool {

    public static final String NAME = "head_file";

    private static final int DEFAULT_LINES = 10;

    public record Request(
        @ToolParam(description = "List of file paths to read the head of", required = false)
        List<String> paths,
        @ToolParam(description = "Regex pattern to match file names in the working directory", required = false)
        String pattern,
        @ToolParam(description = "Number of lines to read from each file (default 10)", required = false)
        Integer lines
    ) {}

    public record FileHead(String path, String content, int lineCount) {}

    public record Response(List<FileHead> results) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Read the first N lines from one or more files"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        Path workDir = context.workingDirectory();
        int lineCount = req.lines() != null ? req.lines() : DEFAULT_LINES;

        List<Path> resolvedPaths = FileToolSupport.resolvePaths(workDir, req.paths(), req.pattern());
        List<FileHead> results = new ArrayList<>();

        for (Path filePath : resolvedPaths) {
            if (!Files.isRegularFile(filePath)) {
                results.add(new FileHead(workDir.relativize(filePath).toString(), "[not a file]", 0));
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                StringBuilder sb = new StringBuilder();
                int count = 0;
                String line;
                while (count < lineCount && (line = reader.readLine()) != null) {
                    if (count > 0) sb.append('\n');
                    sb.append(line);
                    count++;
                }
                results.add(new FileHead(
                        workDir.relativize(filePath).toString().replace('\\', '/'),
                        sb.toString(), count));
            } catch (IOException e) {
                results.add(new FileHead(
                        workDir.relativize(filePath).toString().replace('\\', '/'),
                        "[error: " + e.getMessage() + "]", 0));
            }
        }

        return new Response(results);
    }
}
