package com.troupeforge.core.context;

import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.UserId;

import java.time.Instant;

public record RequestContext(
    RequestId requestId,
    RequestorContext requestor,
    StageContext stage,
    Instant createdAt
) {
    public OrganizationId organizationId() {
        return requestor.organizationId();
    }

    public UserId userId() {
        return requestor.userId();
    }

    public AgentBucketId bucketId() {
        return AgentBucketId.of(organizationId(), stage);
    }
}
