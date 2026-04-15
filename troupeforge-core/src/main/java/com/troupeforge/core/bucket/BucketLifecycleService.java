package com.troupeforge.core.bucket;

import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;

public interface BucketLifecycleService {
    void onboard(OrganizationId org, StageContext stage, BucketConfigDescriptor configDescriptor);
    void reload(AgentBucketId bucketId);
    void teardown(AgentBucketId bucketId);
    BucketHealth health(AgentBucketId bucketId);
}
