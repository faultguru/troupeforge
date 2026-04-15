package com.troupeforge.engine.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelTierDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModelConfigLoaderJsonImpl implements ModelConfigLoader {

    private static final String MODELS_FILE = "models/models.json";
    private final ObjectMapper objectMapper;

    public ModelConfigLoaderJsonImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelConfig loadModelConfig(OrgConfigSource configSource) {
        String content = configSource.readFile(MODELS_FILE);
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Models config file not found: " + MODELS_FILE);
        }
        return parseModelConfig(content);
    }

    ModelConfig parseModelConfig(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Parse aliases
            Map<String, String> aliases = new LinkedHashMap<>();
            JsonNode aliasesNode = root.get("aliases");
            if (aliasesNode != null && aliasesNode.isObject()) {
                var fields = aliasesNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    aliases.put(entry.getKey(), entry.getValue().asText());
                }
            }

            // Parse tiers
            Map<TierId, ModelTierDefinition> tiers = new LinkedHashMap<>();
            JsonNode tiersNode = root.get("tiers");
            if (tiersNode != null && tiersNode.isObject()) {
                var fields = tiersNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    String tierName = entry.getKey();
                    TierId tierId = new TierId(tierName);
                    JsonNode tierNode = entry.getValue();

                    String model = tierNode.has("model") ? tierNode.get("model").asText() : null;
                    int maxTokens = tierNode.has("maxTokens") ? tierNode.get("maxTokens").asInt() : 0;
                    double temperature = tierNode.has("temperature") ? tierNode.get("temperature").asDouble() : 0.7;
                    String description = tierNode.has("description") ? tierNode.get("description").asText() : null;
                    String provider = tierNode.has("provider") ? tierNode.get("provider").asText() : null;

                    tiers.put(tierId, new ModelTierDefinition(tierId, model, maxTokens, temperature, description, provider));
                }
            }

            String fallbackModel = root.has("fallbackModel") ? root.get("fallbackModel").asText() : null;
            int fallbackMaxTokens = root.has("fallbackMaxTokens") ? root.get("fallbackMaxTokens").asInt() : 4096;

            return new ModelConfig(
                    Collections.unmodifiableMap(aliases),
                    Collections.unmodifiableMap(tiers),
                    fallbackModel,
                    fallbackMaxTokens
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse model config: " + e.getMessage(), e);
        }
    }
}
