package com.troupeforge.tools.file;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Grep-like content search across files using regex patterns.
 */
public class SearchFilesTool implements Tool {

    public static final String NAME = "search_files";

    public record Request(
        @ToolParam(description = "Regex pattern to search for")
        String pattern,
        @ToolParam(description = "Glob pattern to filter files (e.g. *.java)", required = false)
        String glob,
        @ToolParam(description = "Maximum number of matches to return (default 50)", required = false)
        Integer maxResults,
        @ToolParam(description = "Number of context lines before and after match (default 0)", required = false)
        Integer contextLines,
        @ToolParam(description = "Case-insensitive search (default false)", required = false)
        Boolean caseInsensitive
    ) {}

    public record Match(String path, int lineNumber, String line, List<String> contextBefore, List<String> contextAfter) {}

    public record Response(List<Match> matches, int totalMatches, boolean truncated) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Search file contents using regex patterns"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        Path workDir = context.workingDirectory();

        int maxResults = req.maxResults() != null ? req.maxResults() : 50;
        int contextLines = req.contextLines() != null ? req.contextLines() : 0;
        boolean caseInsensitive = req.caseInsensitive() != null && req.caseInsensitive();

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        Pattern regex = Pattern.compile(req.pattern(), flags);

        PathMatcher globMatcher = req.glob() != null
                ? FileSystems.getDefault().getPathMatcher("glob:" + req.glob())
                : null;

        List<Match> matches = new ArrayList<>();
        int totalMatches = 0;

        try (Stream<Path> stream = Files.walk(workDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> FileToolSupport.isWithinBase(workDir, p))
                    .filter(p -> {
                        if (globMatcher == null) return true;
                        // Match against both filename and relative path for flexible glob support
                        return globMatcher.matches(p.getFileName())
                                || globMatcher.matches(workDir.relativize(p));
                    })
                    .toList();

            for (Path file : files) {
                List<String> lines;
                try {
                    lines = Files.readAllLines(file);
                } catch (IOException | java.io.UncheckedIOException e) {
                    // skip binary or unreadable files
                    continue;
                }

                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = regex.matcher(lines.get(i));
                    if (m.find()) {
                        totalMatches++;
                        if (matches.size() < maxResults) {
                            List<String> before = new ArrayList<>();
                            for (int b = Math.max(0, i - contextLines); b < i; b++) {
                                before.add(lines.get(b));
                            }
                            List<String> after = new ArrayList<>();
                            for (int a = i + 1; a <= Math.min(lines.size() - 1, i + contextLines); a++) {
                                after.add(lines.get(a));
                            }
                            matches.add(new Match(
                                    workDir.relativize(file).toString().replace('\\', '/'),
                                    i + 1,
                                    lines.get(i),
                                    before,
                                    after));
                        }
                    }
                }
            }
        } catch (IOException e) {
            // skip if directory can't be walked
        }

        return new Response(matches, totalMatches, totalMatches > matches.size());
    }
}
