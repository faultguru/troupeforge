package com.troupeforge.tools.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Shared utilities for file tools: path resolution, traversal protection, and pattern matching.
 */
final class FileToolSupport {

    private FileToolSupport() {}

    /**
     * Resolves a combined list of file paths from explicit paths and/or a regex pattern.
     * All paths are resolved relative to the base directory and checked for traversal.
     */
    static List<Path> resolvePaths(Path baseDir, List<String> explicitPaths, String regexPattern) {
        List<Path> result = new ArrayList<>();

        if (explicitPaths != null) {
            for (String pathStr : explicitPaths) {
                Path resolved = baseDir.resolve(pathStr).normalize();
                if (isWithinBase(baseDir, resolved)) {
                    result.add(resolved);
                }
            }
        }

        if (regexPattern != null && !regexPattern.isBlank()) {
            Pattern regex = Pattern.compile(regexPattern);
            try (Stream<Path> stream = Files.walk(baseDir)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> regex.matcher(p.getFileName().toString()).matches())
                      .forEach(result::add);
            } catch (IOException e) {
                // skip if the directory can't be walked
            }
        }

        return result;
    }

    /**
     * Checks that a resolved path is within the base directory (path traversal protection).
     */
    static boolean isWithinBase(Path baseDir, Path resolved) {
        return resolved.normalize().startsWith(baseDir.normalize());
    }
}
