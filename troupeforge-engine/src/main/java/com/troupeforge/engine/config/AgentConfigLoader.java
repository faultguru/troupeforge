package com.troupeforge.engine.config;

import com.troupeforge.core.agent.AgentDefinition;
import com.troupeforge.core.bucket.OrgConfigSource;

import java.util.List;

public interface AgentConfigLoader {
    List<AgentDefinition> loadAgentDefinitions(OrgConfigSource configSource);
}
