package com.troupeforge.core.message;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.RequestId;

import java.time.Instant;
import java.util.*;

public record MessageEnvelope<T extends Record>(
    MessageId messageId,
    CorrelationId correlationId,
    RequestContext requestContext,
    AgentSessionId senderSessionId,
    AgentAddress sender,
    AgentAddress recipient,
    AgentAddress replyTo,
    ContractRef contractRef,
    MessageType type,
    T payload,
    Instant timestamp,
    Map<String, String> headers,
    int ttlSeconds
) {
    public AgentBucketId bucketId() {
        return requestContext.bucketId();
    }

    public OrganizationId organizationId() {
        return requestContext.organizationId();
    }

    public RequestId requestId() {
        return requestContext.requestId();
    }

    public AgentAddress effectiveReplyTo() {
        return replyTo != null ? replyTo : sender;
    }
}
