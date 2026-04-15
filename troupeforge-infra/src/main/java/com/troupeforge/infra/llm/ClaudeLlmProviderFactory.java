package com.troupeforge.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.llm.LlmProvider;
import com.troupeforge.core.model.ProviderConfig;
import com.troupeforge.core.llm.LlmProviderFactory;

import java.util.List;

public class ClaudeLlmProviderFactory implements LlmProviderFactory {

    private final ObjectMapper objectMapper;

    public ClaudeLlmProviderFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmProvider create(ProviderConfig config) {
        String baseUrl = config.baseUrl() != null ? config.baseUrl() : "https://api.anthropic.com";
        List<String> credentialPaths = config.auth() != null && config.auth().credentialPaths() != null
                ? config.auth().credentialPaths()
                : List.of(System.getProperty("user.home") + "/.claude/.credentials.json");
        return new ClaudeLlmProvider(objectMapper, baseUrl, credentialPaths);
    }

    @Override
    public boolean supports(String providerType) {
        return "oauth".equals(providerType) || "api-key".equals(providerType);
    }
}
