package com.troupeforge.app.rest;

import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final TroupeForgeEntryPoint entryPoint;
    private final AgentBucketRegistry bucketRegistry;

    public ChatController(TroupeForgeEntryPoint entryPoint, AgentBucketRegistry bucketRegistry) {
        this.entryPoint = entryPoint;
        this.bucketRegistry = bucketRegistry;
    }

    public record ChatRequest(String personaId, String message, String sessionId) {}

    public record TokenUsageDto(int inputTokens, int outputTokens, int totalTokens, int cacheReadTokens, int cacheCreationTokens) {}
    public record InferenceDto(String personaId, String model, long latencyMs, int inputTokens, int outputTokens, int totalTokens) {}
    public record ChatResponse(String requestId, String sessionId, String personaId, String response,
                                TokenUsageDto tokenUsage, List<InferenceDto> inferences, long latencyMs) {}

    @PostMapping
    public CompletableFuture<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request received: personaId={}, hasSessionId={}", request.personaId(), request.sessionId() != null);
        log.debug("Chat request details: {}", request);

        if (request.personaId() == null || request.personaId().isBlank()) {
            throw new IllegalArgumentException("personaId must not be empty");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Message must not be empty");
        }

        try {
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

            return entryPoint.submit(requestor, StageContext.LIVE, agentProfileId, request.message(), resumeSessionId)
                    .thenApply(agentResponse -> {
                        log.info("Chat response sent: requestId={}, sessionId={}", agentResponse.requestId().value(), agentResponse.sessionId().value());
                        log.debug("Chat response details: personaId={}, response={}", agentResponse.respondingAgent().personaId().value(), agentResponse.response());
                        TokenUsageDto usageDto = null;
                        if (agentResponse.usage() != null) {
                            var u = agentResponse.usage();
                            usageDto = new TokenUsageDto(u.inputTokens(), u.outputTokens(), u.totalTokens(), u.cacheReadTokens(), u.cacheCreationTokens());
                        }
                        List<InferenceDto> inferenceDtos = agentResponse.inferences() != null
                                ? agentResponse.inferences().stream()
                                    .map(inf -> new InferenceDto(inf.personaId(), inf.model(),
                                            inf.latencyMs(), inf.inputTokens(), inf.outputTokens(), inf.totalTokens()))
                                    .toList()
                                : List.of();
                        return new ChatResponse(
                                agentResponse.requestId().value(),
                                agentResponse.sessionId().value(),
                                agentResponse.respondingAgent().personaId().value(),
                                agentResponse.response(),
                                usageDto,
                                inferenceDtos,
                                agentResponse.latencyMs()
                        );
                    });
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat request failed for personaId={}: {}", request.personaId(), e.getMessage(), e);
            throw e;
        }
    }

}
