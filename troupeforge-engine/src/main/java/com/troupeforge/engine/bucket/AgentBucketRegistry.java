package com.troupeforge.engine.bucket;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;

import java.util.Set;

public interface AgentBucketRegistry {
    AgentBucket getBucket(AgentBucketId bucketId);
    void loadBucket(AgentBucketId bucketId, OrganizationId org, StageContext stage, OrgConfigSource configSource);
    void reloadBucket(AgentBucketId bucketId);
    void unloadBucket(AgentBucketId bucketId);
    Set<AgentBucketId> activeBuckets();
}
