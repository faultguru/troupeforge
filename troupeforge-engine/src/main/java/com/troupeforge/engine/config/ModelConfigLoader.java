package com.troupeforge.engine.config;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.model.ModelConfig;

public interface ModelConfigLoader {
    ModelConfig loadModelConfig(OrgConfigSource configSource);
}
