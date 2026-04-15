package com.troupeforge.app.rest;

import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentBucketRegistry bucketRegistry;

    public AgentController(AgentBucketRegistry bucketRegistry) {
        this.bucketRegistry = bucketRegistry;
    }

    public record AgentInfo(
        String personaId,
        String agentId,
        String name,
        String displayName,
        String description,
        String type
    ) {}

    @GetMapping
    public List<AgentInfo> listAgents(
            @RequestParam(defaultValue = "default") String orgId) {

        AgentBucketId bucketId = AgentBucketId.of(
                new OrganizationId(orgId), StageContext.LIVE);
        AgentBucket bucket = bucketRegistry.getBucket(bucketId);

        List<AgentInfo> agents = new ArrayList<>();
        for (Map.Entry<AgentProfileId, AgentProfile> entry : bucket.agentProfiles().entrySet()) {
            AgentProfile profile = entry.getValue();
            agents.add(new AgentInfo(
                    profile.persona().id().value(),
                    profile.agent().id().value(),
                    profile.agent().name(),
                    profile.effectiveDisplayName(),
                    profile.agent().description(),
                    profile.agent().type().name()
            ));
        }
        return agents;
    }
}
