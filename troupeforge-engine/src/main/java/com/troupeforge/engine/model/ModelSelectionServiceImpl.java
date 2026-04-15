package com.troupeforge.engine.model;

import com.troupeforge.core.id.TierId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelSelection;
import com.troupeforge.core.persona.AgentProfile;

/**
 * Default implementation of {@link ModelSelectionService} that uses a
 * {@link ComplexityAnalyzer} to determine the requested tier and a
 * {@link ModelResolver} to select the actual model.
 */
public class ModelSelectionServiceImpl implements ModelSelectionService {

    private final ModelResolver modelResolver;
    private final ComplexityAnalyzer complexityAnalyzer;
    private final ModelConfig modelConfig;

    /**
     * @param modelResolver      resolves a model from allowed tiers and config
     * @param complexityAnalyzer determines the requested tier from complexity context
     * @param modelConfig        global model configuration with tier definitions and aliases
     */
    public ModelSelectionServiceImpl(ModelResolver modelResolver, ComplexityAnalyzer complexityAnalyzer, ModelConfig modelConfig) {
        this.modelResolver = modelResolver;
        this.complexityAnalyzer = complexityAnalyzer;
        this.modelConfig = modelConfig;
    }

    @Override
    public ModelSelection selectModel(AgentProfile profile, ComplexityContext context) {
        TierId requestedTier = complexityAnalyzer.analyze(context);
        return modelResolver.resolve(profile.allowedTiers(), modelConfig, requestedTier);
    }

    @Override
    public ModelSelection selectModel(AgentProfile profile, ComplexityContext context, ModelConfig overrideModelConfig) {
        TierId requestedTier = complexityAnalyzer.analyze(context);
        return modelResolver.resolve(profile.allowedTiers(), overrideModelConfig, requestedTier);
    }
}
