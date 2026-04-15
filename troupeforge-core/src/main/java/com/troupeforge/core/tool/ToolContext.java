package com.troupeforge.core.tool;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;

import java.nio.file.Path;
import java.util.Map;

public record ToolContext(
    RequestContext requestContext,
    AgentSessionId agentSessionId,
    AgentProfileId profileId,
    Path workingDirectory,
    Map<String, Object> environmentHints
) {}
