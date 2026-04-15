package com.troupeforge.tools.file;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lists files in one or more directories, optionally filtered by regex and recursive descent.
 */
public class ListFilesTool implements Tool {

    public static final String NAME = "list_files";

    public record Request(
        @ToolParam(description = "List of directories to list files from (defaults to working directory)", required = false)
        List<String> directories,
        @ToolParam(description = "Regex pattern to filter file names", required = false)
        String pattern,
        @ToolParam(description = "Whether to list files recursively (default false)", required = false)
        Boolean recursive
    ) {}

    public record FileEntry(String path, long size, boolean directory) {}

    public record Response(List<FileEntry> entries) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "List files in one or more directories with optional regex filtering"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        Path workDir = context.workingDirectory();
        boolean recursive = req.recursive() != null && req.recursive();
        Pattern regex = req.pattern() != null ? Pattern.compile(req.pattern()) : null;

        List<Path> dirs = new ArrayList<>();
        if (req.directories() == null || req.directories().isEmpty()) {
            dirs.add(workDir);
        } else {
            for (String dirStr : req.directories()) {
                Path resolved = workDir.resolve(dirStr).normalize();
                if (FileToolSupport.isWithinBase(workDir, resolved) && Files.isDirectory(resolved)) {
                    dirs.add(resolved);
                }
            }
        }

        List<FileEntry> entries = new ArrayList<>();
        for (Path dir : dirs) {
            try {
                Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir);
                try (stream) {
                    stream.filter(p -> !p.equals(dir))
                          .filter(p -> {
                              if (regex == null) return true;
                              String name = p.getFileName().toString();
                              return regex.matcher(name).matches();
                          })
                          .forEach(p -> {
                              try {
                                  entries.add(new FileEntry(
                                          workDir.relativize(p).toString().replace('\\', '/'),
                                          Files.isRegularFile(p) ? Files.size(p) : 0,
                                          Files.isDirectory(p)));
                              } catch (IOException e) {
                                  entries.add(new FileEntry(
                                          workDir.relativize(p).toString().replace('\\', '/'),
                                          0, false));
                              }
                          });
                }
            } catch (IOException e) {
                // skip directories that can't be listed
            }
        }

        return new Response(entries);
    }
}
