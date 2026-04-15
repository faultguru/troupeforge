package com.troupeforge.core;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.message.AgentAddress;
import com.troupeforge.core.message.CorrelationId;
import com.troupeforge.core.message.MessageEnvelope;
import com.troupeforge.core.message.MessageId;
import com.troupeforge.core.message.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Message Envelope Correctness")
class MessageEnvelopeTest {

    private static final AgentProfileId SENDER_PROFILE =
            new AgentProfileId(new AgentId("dispatcher"), new PersonaId("linda"));
    private static final AgentProfileId RECIPIENT_PROFILE =
            new AgentProfileId(new AgentId("greeter"), new PersonaId("simon"));
    private static final AgentProfileId REPLY_TO_PROFILE =
            new AgentProfileId(new AgentId("root"), new PersonaId("default"));

    private RequestContext makeRequestContext(OrganizationId org) {
        return new RequestContext(
                RequestId.generate(),
                new RequestorContext(new UserId("test-user"), org),
                StageContext.LIVE,
                Instant.now()
        );
    }

    private <T extends Record> MessageEnvelope<T> makeEnvelope(
            RequestContext reqCtx, AgentAddress replyTo, T payload) {
        return new MessageEnvelope<>(
                MessageId.generate(),
                CorrelationId.generate(),
                reqCtx,
                AgentSessionId.generate(),
                new AgentAddress.Direct(SENDER_PROFILE),
                new AgentAddress.Direct(RECIPIENT_PROFILE),
                replyTo,
                null,
                MessageType.REQUEST,
                payload,
                Instant.now(),
                Map.of(),
                30
        );
    }

    // A simple payload record for testing
    record TestPayload(String message) {}

    @Test
    void testEffectiveReplyToUsesReplyToWhenSet() {
        RequestContext reqCtx = makeRequestContext(new OrganizationId("test-org"));
        AgentAddress explicitReplyTo = new AgentAddress.Direct(REPLY_TO_PROFILE);

        MessageEnvelope<TestPayload> envelope = makeEnvelope(
                reqCtx, explicitReplyTo, new TestPayload("hello"));

        AgentAddress effective = envelope.effectiveReplyTo();
        assertEquals(explicitReplyTo, effective);
    }

    @Test
    void testEffectiveReplyToFallsBackToSender() {
        RequestContext reqCtx = makeRequestContext(new OrganizationId("test-org"));

        MessageEnvelope<TestPayload> envelope = makeEnvelope(
                reqCtx, null, new TestPayload("hello"));

        AgentAddress effective = envelope.effectiveReplyTo();
        // When replyTo is null, effectiveReplyTo should fall back to sender
        assertEquals(new AgentAddress.Direct(SENDER_PROFILE), effective);
    }

    @Test
    void testBucketIdDerivedFromRequestContext() {
        OrganizationId org = new OrganizationId("acme-corp");
        RequestContext reqCtx = makeRequestContext(org);

        MessageEnvelope<TestPayload> envelope = makeEnvelope(
                reqCtx, null, new TestPayload("test"));

        AgentBucketId expectedBucketId = AgentBucketId.of(org, StageContext.LIVE);
        assertEquals(expectedBucketId, envelope.bucketId());
        assertEquals("acme-corp:live", envelope.bucketId().value());
    }
}
