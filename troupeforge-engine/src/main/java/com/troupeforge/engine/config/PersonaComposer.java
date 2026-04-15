package com.troupeforge.engine.config;

import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.persona.PersonaDefinition;

public interface PersonaComposer {
    AgentProfile compose(ResolvedAgent agent, PersonaDefinition persona);
}
