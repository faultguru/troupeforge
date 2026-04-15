package com.troupeforge.app.lifecycle;

import com.troupeforge.core.bucket.BucketConfigDescriptor;
import com.troupeforge.core.bucket.BucketHealth;
import com.troupeforge.core.bucket.BucketLifecycleService;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.infra.filesystem.FilesystemOrgConfigSource;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;

@Component
public class BucketLifecycleServiceImpl implements BucketLifecycleService {

    private final AgentBucketRegistry bucketRegistry;

    public BucketLifecycleServiceImpl(AgentBucketRegistry bucketRegistry) {
        this.bucketRegistry = bucketRegistry;
    }

    @Override
    public void onboard(OrganizationId org, StageContext stage, BucketConfigDescriptor configDescriptor) {
        AgentBucketId bucketId = AgentBucketId.of(org, stage);
        String sourceType = configDescriptor.sourceType();

        OrgConfigSource configSource;
        if ("filesystem".equals(sourceType)) {
            Path basePath = Path.of(configDescriptor.properties().get("basePath"));
            configSource = new FilesystemOrgConfigSource(org, stage, basePath);
        } else {
            throw new IllegalArgumentException("Unsupported config source type: " + sourceType);
        }

        bucketRegistry.loadBucket(bucketId, org, stage, configSource);
    }

    @Override
    public void reload(AgentBucketId bucketId) {
        bucketRegistry.reloadBucket(bucketId);
    }

    @Override
    public void teardown(AgentBucketId bucketId) {
        bucketRegistry.unloadBucket(bucketId);
    }

    @Override
    public BucketHealth health(AgentBucketId bucketId) {
        try {
            AgentBucket bucket = bucketRegistry.getBucket(bucketId);
            return new BucketHealth(
                    bucketId,
                    bucket.organizationId(),
                    bucket.stage(),
                    true,
                    true,
                    bucket.agentProfiles().size(),
                    0,
                    bucket.loadedAt()
            );
        } catch (Exception e) {
            return new BucketHealth(
                    bucketId,
                    null,
                    null,
                    false,
                    false,
                    0,
                    0,
                    Instant.now()
            );
        }
    }
}
