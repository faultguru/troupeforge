package com.troupeforge.app.rest;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.storage.QueryCriteria;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ContextStore contextStore;

    public SessionController(ContextStore contextStore) {
        this.contextStore = contextStore;
    }

    public record SessionInfo(
        String sessionId,
        String agentId,
        String personaId,
        String startedAt,
        int historySize
    ) {}

    @GetMapping
    public List<SessionInfo> listSessions(
            @RequestParam(defaultValue = "default") String orgId) {

        AgentBucketId bucketId = AgentBucketId.of(
                new OrganizationId(orgId), StageContext.LIVE);
        List<AgentContext> contexts = contextStore.findByBucket(bucketId, QueryCriteria.all());

        return contexts.stream()
                .map(ctx -> new SessionInfo(
                        ctx.sessionId().value(),
                        ctx.agentProfileId().agentId().value(),
                        ctx.agentProfileId().personaId().value(),
                        ctx.startedAt() != null ? ctx.startedAt().toString() : null,
                        ctx.conversationHistory() != null ? ctx.conversationHistory().size() : 0
                ))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        contextStore.delete(new AgentSessionId(sessionId));
        return ResponseEntity.ok(Map.of("status", "deleted", "sessionId", sessionId));
    }
}
