package com.troupeforge.engine.model;

import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelSelection;
import com.troupeforge.core.persona.AgentProfile;

public interface ModelSelectionService {
    ModelSelection selectModel(AgentProfile profile, ComplexityContext context);

    /**
     * Selects a model using the provided ModelConfig instead of the default one.
     */
    ModelSelection selectModel(AgentProfile profile, ComplexityContext context, ModelConfig modelConfig);
}
