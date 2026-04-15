package com.troupeforge.core.id;

import com.troupeforge.core.context.StageContext;

public record AgentBucketId(String value) {
    public static AgentBucketId of(OrganizationId org, StageContext stage) {
        return new AgentBucketId(org.value() + ":" + stage.value());
    }
}
