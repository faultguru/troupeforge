package com.troupeforge.app.rest;

import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.storage.QueryCriteria;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    private final AgentBucketRegistry bucketRegistry;
    private final ContextStore contextStore;

    public StatusController(AgentBucketRegistry bucketRegistry, ContextStore contextStore) {
        this.bucketRegistry = bucketRegistry;
        this.contextStore = contextStore;
    }

    public record StatusResponse(
        String status,
        int agentCount,
        int activeSessionCount,
        String uptime,
        Map<String, Object> runtime
    ) {}

    @GetMapping
    public StatusResponse getStatus() {
        AgentBucketId bucketId = AgentBucketId.of(
                new OrganizationId("default"), StageContext.LIVE);

        int agentCount = 0;
        try {
            AgentBucket bucket = bucketRegistry.getBucket(bucketId);
            agentCount = bucket.agentProfiles().size();
        } catch (Exception e) {
            log.warn("Failed to load agent count for status: {}", e.getMessage());
        }

        int sessionCount = 0;
        try {
            sessionCount = contextStore.findByBucket(bucketId, QueryCriteria.all()).size();
        } catch (Exception e) {
            log.warn("Failed to load session count for status: {}", e.getMessage());
        }

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        String uptimeStr = String.format("%dd %dh %dm %ds",
                uptime.toDays(), uptime.toHoursPart(),
                uptime.toMinutesPart(), uptime.toSecondsPart());

        Map<String, Object> runtime = Map.of(
                "javaVersion", System.getProperty("java.version"),
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "maxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024),
                "freeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024)
        );

        return new StatusResponse("UP", agentCount, sessionCount, uptimeStr, runtime);
    }
}
