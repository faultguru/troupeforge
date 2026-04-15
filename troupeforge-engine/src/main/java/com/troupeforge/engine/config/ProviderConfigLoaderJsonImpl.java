package com.troupeforge.engine.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.model.ProviderConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProviderConfigLoaderJsonImpl implements ProviderConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigLoaderJsonImpl.class);

    private static final String PROVIDERS_DIR = "models/providers";

    private final ObjectMapper objectMapper;

    public ProviderConfigLoaderJsonImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, ProviderConfig> loadProviderConfigs(OrgConfigSource configSource) {
        List<String> files = configSource.listFiles(PROVIDERS_DIR);
        Map<String, ProviderConfig> result = new LinkedHashMap<>();

        for (String filePath : files) {
            if (!filePath.endsWith(".json")) {
                continue;
            }
            try {
                String content = configSource.readFile(filePath);
                ProviderConfig config = parseProviderConfig(content);
                result.put(config.id(), config);
                log.debug("Loaded provider config: id={}, name={}, type={}", config.id(), config.name(), config.type());
            } catch (Exception e) {
                log.warn("Failed to parse provider config from {}: {}", filePath, e.getMessage());
            }
        }

        log.info("Loaded {} provider configs", result.size());
        return Collections.unmodifiableMap(result);
    }

    ProviderConfig parseProviderConfig(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String id = root.get("id").asText();
            String name = root.has("name") ? root.get("name").asText() : id;
            String type = root.has("type") ? root.get("type").asText() : "api-key";
            String baseUrl = root.has("baseUrl") ? root.get("baseUrl").asText() : null;

            // Parse auth
            ProviderConfig.AuthConfig authConfig = null;
            JsonNode authNode = root.get("auth");
            if (authNode != null && authNode.isObject()) {
                String method = authNode.has("method") ? authNode.get("method").asText() : "api-key";
                List<String> credentialPaths = new ArrayList<>();
                JsonNode pathsNode = authNode.get("credentialPaths");
                if (pathsNode != null && pathsNode.isArray()) {
                    for (JsonNode pathNode : pathsNode) {
                        credentialPaths.add(pathNode.asText());
                    }
                }
                authConfig = new ProviderConfig.AuthConfig(method, Collections.unmodifiableList(credentialPaths));
            }

            // Parse supportedModels
            List<String> supportedModels = new ArrayList<>();
            JsonNode modelsNode = root.get("supportedModels");
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode modelNode : modelsNode) {
                    supportedModels.add(modelNode.asText());
                }
            }

            // Parse headers
            Map<String, String> headers = new LinkedHashMap<>();
            JsonNode headersNode = root.get("headers");
            if (headersNode != null && headersNode.isObject()) {
                var fields = headersNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    headers.put(entry.getKey(), entry.getValue().asText());
                }
            }

            return new ProviderConfig(
                    id, name, type, baseUrl, authConfig,
                    Collections.unmodifiableList(supportedModels),
                    Collections.unmodifiableMap(headers)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse provider config: " + e.getMessage(), e);
        }
    }
}
