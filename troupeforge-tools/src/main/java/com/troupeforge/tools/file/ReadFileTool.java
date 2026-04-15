package com.troupeforge.tools.file;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the full contents of one or more files.
 * Accepts explicit file paths and/or a regex pattern to match filenames.
 */
public class ReadFileTool implements Tool {

    public static final String NAME = "read_file";

    public record Request(
        @ToolParam(description = "List of file paths to read", required = false)
        List<String> paths,
        @ToolParam(description = "Regex pattern to match file names in the working directory", required = false)
        String pattern
    ) {}

    public record FileContent(String path, String content) {}

    public record Response(List<FileContent> files) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Read the full contents of one or more files"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        Path workDir = context.workingDirectory();

        List<Path> resolvedPaths = FileToolSupport.resolvePaths(workDir, req.paths(), req.pattern());
        List<FileContent> results = new ArrayList<>();

        for (Path filePath : resolvedPaths) {
            if (!Files.isRegularFile(filePath)) {
                results.add(new FileContent(
                        workDir.relativize(filePath).toString().replace('\\', '/'),
                        "[not a file]"));
                continue;
            }
            try {
                String content = Files.readString(filePath);
                results.add(new FileContent(
                        workDir.relativize(filePath).toString().replace('\\', '/'),
                        content));
            } catch (IOException e) {
                results.add(new FileContent(
                        workDir.relativize(filePath).toString().replace('\\', '/'),
                        "[error: " + e.getMessage() + "]"));
            }
        }

        return new Response(results);
    }
}
