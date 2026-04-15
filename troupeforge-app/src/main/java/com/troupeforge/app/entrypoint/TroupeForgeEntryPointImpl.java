package com.troupeforge.app.entrypoint;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.entrypoint.AgentResponse;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.engine.execution.AgentExecutor;
import com.troupeforge.engine.session.AgentSessionFactory;
import com.troupeforge.core.context.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class TroupeForgeEntryPointImpl implements TroupeForgeEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(TroupeForgeEntryPointImpl.class);

    private final AgentExecutor agentExecutor;
    private final AgentSessionFactory sessionFactory;

    public TroupeForgeEntryPointImpl(AgentExecutor agentExecutor, AgentSessionFactory sessionFactory) {
        this.agentExecutor = agentExecutor;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CompletableFuture<AgentResponse> submit(
            RequestorContext requestor,
            StageContext stage,
            AgentProfileId targetAgent,
            String message,
            AgentSessionId resumeSessionId) {

        log.info("Submit called: targetAgent={}, hasResumeSession={}", targetAgent.toKey(), resumeSessionId != null);
        log.debug("Submit details: requestor={}, stage={}, message={}", requestor, stage, message);

        RequestContext requestContext = new RequestContext(
                RequestId.generate(),
                requestor,
                stage,
                Instant.now()
        );

        final AgentContext session = resolveSession(resumeSessionId, requestContext, targetAgent);

        return agentExecutor.execute(requestContext, session.sessionId(), message)
                .thenApply(result -> {
                    log.info("Submit completed: requestId={}, respondingAgent={}",
                            requestContext.requestId().value(), result.respondingAgent().toKey());
                    log.debug("Submit response: {}", result.response());
                    Instant now = Instant.now();
                    long latencyMs = java.time.Duration.between(requestContext.createdAt(), now).toMillis();
                    // For handovers, use the child session ID so the caller resumes with the new agent
                    AgentSessionId effectiveSessionId = result.handoverSessionId() != null
                            ? result.handoverSessionId()
                            : session.sessionId();
                    // Map inference summaries to DTOs
                    var inferenceDtos = result.inferences() != null
                            ? result.inferences().stream().map(inf ->
                                new AgentResponse.InferenceSummaryDto(
                                        inf.personaId(), inf.model(), inf.latencyMs(),
                                        inf.usage() != null ? inf.usage().inputTokens() : 0,
                                        inf.usage() != null ? inf.usage().outputTokens() : 0,
                                        inf.usage() != null ? inf.usage().totalTokens() : 0))
                                .toList()
                            : java.util.List.<AgentResponse.InferenceSummaryDto>of();
                    return new AgentResponse(
                            requestContext.requestId(),
                            effectiveSessionId,
                            result.respondingAgent(),
                            result.response(),
                            now,
                            result.totalUsage(),
                            inferenceDtos,
                            latencyMs
                    );
                });
    }

    private AgentContext resolveSession(AgentSessionId resumeSessionId,
                                        RequestContext requestContext,
                                        AgentProfileId targetAgent) {
        if (resumeSessionId == null) {
            return sessionFactory.newSession(requestContext, targetAgent);
        }
        try {
            return sessionFactory.resumeSession(resumeSessionId);
        } catch (IllegalStateException e) {
            log.warn("Session not found (expired?): {}. Starting new session.", resumeSessionId.value());
            return sessionFactory.newSession(requestContext, targetAgent);
        }
    }
}
