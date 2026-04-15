package com.troupeforge.tests.support;

import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.infra.filesystem.FilesystemOrgConfigSource;
import com.troupeforge.core.bucket.OrgConfigSource;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestBucketHelper {

    public static final OrganizationId TEST_ORG = new OrganizationId("test-org");
    public static final StageContext TEST_STAGE = StageContext.LIVE;
    public static final AgentBucketId TEST_BUCKET_ID = AgentBucketId.of(TEST_ORG, TEST_STAGE);

    public static final AgentProfileId GREETER_SIMON =
            new AgentProfileId(new AgentId("greeter"), new PersonaId("simon"));
    public static final AgentProfileId GREETER_BOND =
            new AgentProfileId(new AgentId("greeter"), new PersonaId("bond"));
    public static final AgentProfileId GREETER_LORD =
            new AgentProfileId(new AgentId("greeter"), new PersonaId("lord"));
    public static final AgentProfileId DISPATCHER_LINDA =
            new AgentProfileId(new AgentId("dispatcher"), new PersonaId("linda"));
    public static final AgentProfileId ARCHITECT_SOFIA =
            new AgentProfileId(new AgentId("architect"), new PersonaId("sofia"));
    public static final AgentProfileId RESEARCHER_GURU =
            new AgentProfileId(new AgentId("researcher"), new PersonaId("guru"));
    public static final AgentProfileId MOCK_AGENT_CLARA =
            new AgentProfileId(new AgentId("mock-agent"), new PersonaId("clara"));
    public static final AgentProfileId MOCK_AGENT_MAX =
            new AgentProfileId(new AgentId("mock-agent"), new PersonaId("max"));
    public static final AgentProfileId CALCULATOR_ALBERT =
            new AgentProfileId(new AgentId("calculator"), new PersonaId("albert"));
    public static final AgentProfileId ECHO_PETE =
            new AgentProfileId(new AgentId("echo"), new PersonaId("pete"));

    public static void loadTestBucket(AgentBucketRegistry registry) {
        Path configPath = findTestConfigPath();
        OrgConfigSource configSource = new FilesystemOrgConfigSource(TEST_ORG, TEST_STAGE, configPath);
        registry.loadBucket(TEST_BUCKET_ID, TEST_ORG, TEST_STAGE, configSource);
    }

    static Path findTestConfigPath() {
        URL classpathUrl = TestBucketHelper.class.getClassLoader().getResource("config/agents");
        if (classpathUrl != null) {
            try {
                Path agentsPath = Path.of(classpathUrl.toURI());
                Path configPath = agentsPath.getParent();
                if (Files.isDirectory(configPath)) {
                    return configPath;
                }
            } catch (URISyntaxException | java.nio.file.FileSystemNotFoundException e) {
                // fall through to relative path attempts
            }
        }

        String[] relativePaths = {
                "troupeforge-testconfig/src/main/resources/config",
                "../troupeforge-testconfig/src/main/resources/config",
                "../../troupeforge-testconfig/src/main/resources/config",
        };

        for (String relativePath : relativePaths) {
            Path candidate = Path.of(relativePath).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate) && Files.isDirectory(candidate.resolve("agents"))) {
                return candidate;
            }
        }

        Path cwd = Path.of("").toAbsolutePath();
        Path projectRoot = cwd;
        for (int i = 0; i < 5; i++) {
            Path candidate = projectRoot.resolve("troupeforge-testconfig/src/main/resources/config");
            if (Files.isDirectory(candidate) && Files.isDirectory(candidate.resolve("agents"))) {
                return candidate;
            }
            projectRoot = projectRoot.getParent();
            if (projectRoot == null) {
                break;
            }
        }

        throw new IllegalStateException(
                "Could not locate test config directory. Ensure troupeforge-testconfig module " +
                "is available on the classpath or the working directory is within the project tree. " +
                "Current working directory: " + cwd);
    }
}
