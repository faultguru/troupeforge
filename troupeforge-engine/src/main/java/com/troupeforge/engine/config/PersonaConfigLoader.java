package com.troupeforge.engine.config;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.persona.PersonaDefinition;

import java.util.List;
import java.util.Map;

public interface PersonaConfigLoader {
    Map<AgentId, List<PersonaDefinition>> loadPersonaDefinitions(OrgConfigSource configSource);
}
