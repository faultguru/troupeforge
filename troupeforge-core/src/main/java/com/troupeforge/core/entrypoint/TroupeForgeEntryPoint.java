package com.troupeforge.core.entrypoint;

import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;

import java.util.concurrent.CompletableFuture;

public interface TroupeForgeEntryPoint {
    CompletableFuture<AgentResponse> submit(
        RequestorContext requestor,
        StageContext stage,
        AgentProfileId targetAgent,
        String message,
        AgentSessionId resumeSessionId
    );
}
