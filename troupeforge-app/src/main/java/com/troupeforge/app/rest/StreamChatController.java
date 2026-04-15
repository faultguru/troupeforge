package com.troupeforge.app.rest;

import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.llm.LlmStreamEvent;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.stream.StreamingAgentExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Streaming chat endpoint using Server-Sent Events.
 */
@RestController
@RequestMapping("/api/chat/stream")
public class StreamChatController {

    private static final Logger log = LoggerFactory.getLogger(StreamChatController.class);

    private final StreamingAgentExecutor streamingExecutor;
    private final AgentBucketRegistry bucketRegistry;

    public StreamChatController(StreamingAgentExecutor streamingExecutor, AgentBucketRegistry bucketRegistry) {
        this.streamingExecutor = streamingExecutor;
        this.bucketRegistry = bucketRegistry;
    }

    public record StreamChatRequest(String personaId, String message, String sessionId) {}

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> streamChat(@RequestBody StreamChatRequest request) {
        log.info("Stream chat request received: personaId={}", request.personaId());

        if (request.personaId() == null || request.personaId().isBlank()) {
            return Flux.error(new IllegalArgumentException("personaId must not be empty"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return Flux.error(new IllegalArgumentException("Message must not be empty"));
        }

        OrganizationId org = new OrganizationId("default");
        UserId userId = new UserId("api-user");
        RequestorContext requestor = new RequestorContext(userId, org);

        AgentBucketId bucketId = AgentBucketId.of(org, StageContext.LIVE);
        AgentBucket bucket = bucketRegistry.getBucket(bucketId);

        PersonaId personaId = new PersonaId(request.personaId());
        Map.Entry<AgentProfileId, AgentProfile> entry = bucket.findProfileByPersonaId(personaId);
        AgentProfileId agentProfileId = entry.getKey();

        AgentSessionId resumeSessionId = request.sessionId() != null
                ? new AgentSessionId(request.sessionId())
                : null;

        return streamingExecutor.executeStream(requestor, StageContext.LIVE, agentProfileId,
                        request.message(), resumeSessionId)
                .map(StreamChatController::toStreamEvent);
    }

    /**
     * SSE event wrapper for client consumption.
     */
    public sealed interface StreamEvent permits StreamEvent.TextDelta, StreamEvent.ToolCall,
            StreamEvent.Done, StreamEvent.Error {
        record TextDelta(String text) implements StreamEvent {}
        record ToolCall(String toolCallId, String name, String argumentsChunk) implements StreamEvent {}
        record Done(String sessionId, String personaId, int totalTokens, long latencyMs) implements StreamEvent {}
        record Error(String message) implements StreamEvent {}
    }

    private static StreamEvent toStreamEvent(LlmStreamEvent event) {
        return switch (event) {
            case LlmStreamEvent.ContentDelta d -> new StreamEvent.TextDelta(d.text());
            case LlmStreamEvent.ToolCallDelta d -> new StreamEvent.ToolCall(d.toolCallId(), d.name(), d.argumentsChunk());
            case LlmStreamEvent.Complete c -> new StreamEvent.Done(
                    null, null,
                    c.response().usage() != null ? c.response().usage().totalTokens() : 0,
                    c.response().latency() != null ? c.response().latency().toMillis() : 0);
            case LlmStreamEvent.StreamError e -> new StreamEvent.Error(
                    e.cause() != null ? e.cause().getMessage() : "Unknown streaming error");
        };
    }
}
