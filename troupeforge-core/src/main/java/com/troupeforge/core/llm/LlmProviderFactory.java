package com.troupeforge.core.llm;

import com.troupeforge.core.model.ProviderConfig;

public interface LlmProviderFactory {
    LlmProvider create(ProviderConfig config);
    boolean supports(String providerType);
}
