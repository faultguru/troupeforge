package com.troupeforge.engine.bucket;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.OrganizationId;

public interface AgentBucketLoader {
    AgentBucket load(OrganizationId org, StageContext stage, OrgConfigSource configSource);
}
