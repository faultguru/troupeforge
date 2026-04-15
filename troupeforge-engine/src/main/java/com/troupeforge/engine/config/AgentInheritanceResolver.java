package com.troupeforge.engine.config;

import com.troupeforge.core.agent.AgentDefinition;
import com.troupeforge.core.agent.ResolvedAgent;

import java.util.List;

public interface AgentInheritanceResolver {
    List<ResolvedAgent> resolve(List<AgentDefinition> definitions);
}
