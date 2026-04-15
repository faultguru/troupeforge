package com.troupeforge.app.config;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.infra.filesystem.FilesystemOrgConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class BucketAutoLoadConfig {

    private static final Logger log = LoggerFactory.getLogger(BucketAutoLoadConfig.class);

    @Bean
    @ConditionalOnProperty(name = "troupeforge.bucket.auto-load.enabled", havingValue = "true")
    public CommandLineRunner bucketAutoLoader(
            AgentBucketRegistry bucketRegistry,
            @Value("${troupeforge.bucket.auto-load.config-path}") String configPath,
            @Value("${troupeforge.bucket.auto-load.org-id:default}") String orgId) {
        return args -> {
            OrganizationId org = new OrganizationId(orgId);
            StageContext stage = StageContext.LIVE;
            AgentBucketId bucketId = AgentBucketId.of(org, stage);
            Path path = Path.of(configPath);
            OrgConfigSource source = new FilesystemOrgConfigSource(org, stage, path);
            bucketRegistry.loadBucket(bucketId, org, stage, source);
            log.info("Auto-loaded bucket [{}] from {}", bucketId, path);
        };
    }
}
