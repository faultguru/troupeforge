package com.troupeforge.engine.session;

import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;

public interface AgentSessionFactory {
    AgentContext newSession(RequestContext request, AgentProfileId profileId);
    AgentContext newDelegatedSession(RequestContext request, AgentProfileId profileId, AgentSessionId parentSessionId);
    AgentContext resumeSession(AgentSessionId sessionId);
}
