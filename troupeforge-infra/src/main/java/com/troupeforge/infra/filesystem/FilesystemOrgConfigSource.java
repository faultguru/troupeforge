package com.troupeforge.infra.filesystem;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.OrganizationId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FilesystemOrgConfigSource implements OrgConfigSource {

    private final OrganizationId organizationId;
    private final StageContext stage;
    private final Path basePath;

    public FilesystemOrgConfigSource(OrganizationId organizationId, StageContext stage, Path basePath) {
        this.organizationId = organizationId;
        this.stage = stage;
        this.basePath = basePath;
    }

    @Override
    public OrganizationId organizationId() {
        return organizationId;
    }

    @Override
    public StageContext stage() {
        return stage;
    }

    @Override
    public List<String> listAgentDirectories(String parentPath) {
        Path dir = basePath.resolve(parentPath);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && containsAgentJson(entry)) {
                    result.add(basePath.relativize(entry).toString().replace('\\', '/'));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list agent directories under " + dir, e);
        }
        return result;
    }

    private boolean containsAgentJson(Path directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*-agent.json")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String readFile(String path) {
        Path filePath = basePath.resolve(path);
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file " + filePath, e);
        }
    }

    @Override
    public List<String> listFiles(String directory) {
        Path dir = basePath.resolve(directory);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    result.add(basePath.relativize(entry).toString().replace('\\', '/'));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list files in " + dir, e);
        }
        return result;
    }

    @Override
    public Optional<String> configVersion() {
        return Optional.empty();
    }
}
