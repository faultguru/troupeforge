package com.troupeforge.core.bucket;

import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;

import java.time.Instant;

public record BucketHealth(
    AgentBucketId bucketId,
    OrganizationId organizationId,
    StageContext stage,
    boolean configLoaded,
    boolean llmAvailable,
    int activeAgents,
    int activeSessions,
    Instant lastActivity
) {}
