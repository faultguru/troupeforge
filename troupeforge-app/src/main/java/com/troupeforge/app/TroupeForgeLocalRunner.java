package com.troupeforge.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local development runner that auto-loads the test config bucket on startup.
 * Run this class directly from your IDE — no VM args needed.
 */
@SpringBootApplication(scanBasePackages = "com.troupeforge")
public class TroupeForgeLocalRunner {

    private static final String RELATIVE_CONFIG = "troupeforge-testconfig/src/main/resources/config";

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "local");
        System.setProperty("troupeforge.bucket.auto-load.enabled", "true");
        System.setProperty("troupeforge.bucket.auto-load.config-path", findConfigPath());

        SpringApplication.run(TroupeForgeLocalRunner.class, args);
    }

    private static String findConfigPath() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(RELATIVE_CONFIG);
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
        }
        throw new IllegalStateException(
                "Could not find " + RELATIVE_CONFIG + " from " + cwd);
    }
}
