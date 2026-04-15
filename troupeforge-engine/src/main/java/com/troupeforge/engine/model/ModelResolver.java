package com.troupeforge.engine.model;

import com.troupeforge.core.id.TierId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelSelection;

import java.util.List;

public interface ModelResolver {
    ModelSelection resolve(List<TierId> allowedTiers, ModelConfig globalConfig, TierId requestedTier);
}
