package com.troupeforge.engine.bucket;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AgentBucketRegistryImpl implements AgentBucketRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentBucketRegistryImpl.class);

    private final AgentBucketLoader bucketLoader;
    private final ConcurrentHashMap<AgentBucketId, AgentBucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AgentBucketId, BucketLoadDescriptor> loadDescriptors = new ConcurrentHashMap<>();

    public AgentBucketRegistryImpl(AgentBucketLoader bucketLoader) {
        this.bucketLoader = Objects.requireNonNull(bucketLoader, "bucketLoader must not be null");
    }

    @Override
    public AgentBucket getBucket(AgentBucketId bucketId) {
        AgentBucket bucket = buckets.get(bucketId);
        if (bucket == null) {
            throw new IllegalStateException("Bucket not loaded: " + bucketId.value());
        }
        return bucket;
    }

    @Override
    public void loadBucket(AgentBucketId bucketId, OrganizationId org, StageContext stage,
                           OrgConfigSource configSource) {
        loadDescriptors.put(bucketId, new BucketLoadDescriptor(org, stage, configSource));
        AgentBucket bucket = bucketLoader.load(org, stage, configSource);
        buckets.put(bucketId, bucket);
        log.info("Bucket loaded: bucketId={}, profiles={}", bucketId.value(), bucket.agentProfiles().size());
        log.debug("Bucket profile details: {}", bucket.agentProfiles().keySet());
    }

    @Override
    public void reloadBucket(AgentBucketId bucketId) {
        BucketLoadDescriptor descriptor = loadDescriptors.get(bucketId);
        if (descriptor == null) {
            throw new IllegalStateException(
                "Cannot reload bucket " + bucketId.value() + ": no load descriptor found. "
                    + "The bucket must be loaded at least once before it can be reloaded.");
        }
        AgentBucket bucket = bucketLoader.load(descriptor.org, descriptor.stage, descriptor.configSource);
        buckets.put(bucketId, bucket);
        log.info("Bucket reloaded: bucketId={}, profiles={}", bucketId.value(), bucket.agentProfiles().size());
    }

    @Override
    public void unloadBucket(AgentBucketId bucketId) {
        buckets.remove(bucketId);
        loadDescriptors.remove(bucketId);
        log.info("Bucket unloaded: bucketId={}", bucketId.value());
    }

    @Override
    public Set<AgentBucketId> activeBuckets() {
        return Set.copyOf(buckets.keySet());
    }

    private record BucketLoadDescriptor(OrganizationId org, StageContext stage, OrgConfigSource configSource) {}
}
