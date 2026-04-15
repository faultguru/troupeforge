package com.troupeforge.engine.prompt;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.persona.AgentProfile;

public interface PromptAssembler {
    String assemble(RequestContext requestContext, AgentProfile profile);
}
