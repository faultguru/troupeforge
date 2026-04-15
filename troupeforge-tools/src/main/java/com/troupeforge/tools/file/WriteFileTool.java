package com.troupeforge.tools.file;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates or overwrites a file with the specified content.
 */
public class WriteFileTool implements Tool {

    public static final String NAME = "write_file";

    public record Request(
        @ToolParam(description = "File path relative to working directory")
        String path,
        @ToolParam(description = "Content to write to the file")
        String content,
        @ToolParam(description = "Create parent directories if they don't exist (default true)", required = false)
        Boolean createDirectories
    ) {}

    public record Response(String path, long bytesWritten, boolean created) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Create or overwrite a file with the specified content"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        Path workDir = context.workingDirectory();
        Path resolved = workDir.resolve(req.path()).normalize();

        if (!FileToolSupport.isWithinBase(workDir, resolved)) {
            throw new IllegalArgumentException("Path is outside the working directory: " + req.path());
        }

        boolean createDirs = req.createDirectories() == null || req.createDirectories();
        boolean existed = Files.exists(resolved);

        try {
            if (createDirs && resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }
            Files.writeString(resolved, req.content());
            long bytesWritten = Files.size(resolved);
            return new Response(
                    workDir.relativize(resolved).toString().replace('\\', '/'),
                    bytesWritten,
                    !existed);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }
    }
}
