package com.troupeforge.engine.config;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.model.ProviderConfig;

import java.util.Map;

public interface ProviderConfigLoader {
    Map<String, ProviderConfig> loadProviderConfigs(OrgConfigSource configSource);
}
