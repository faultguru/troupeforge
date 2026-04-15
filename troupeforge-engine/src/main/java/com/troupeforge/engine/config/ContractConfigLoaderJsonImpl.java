package com.troupeforge.engine.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.contract.ContractDefinition;
import com.troupeforge.core.contract.ContractId;
import com.troupeforge.core.contract.ContractVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContractConfigLoaderJsonImpl implements ContractConfigLoader {

    private static final String CONTRACTS_DIR = "contracts";
    private final ObjectMapper objectMapper;

    public ContractConfigLoaderJsonImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ContractDefinition> loadContracts(OrgConfigSource configSource) {
        List<ContractDefinition> contracts = new ArrayList<>();
        List<String> files;
        try {
            files = configSource.listFiles(CONTRACTS_DIR);
        } catch (Exception e) {
            return Collections.emptyList();
        }

        for (String file : files) {
            if (file.endsWith("-contract.json")) {
                String content = configSource.readFile(file);
                if (content != null && !content.isBlank()) {
                    ContractDefinition def = parseContractDefinition(content);
                    if (def != null) {
                        contracts.add(def);
                    }
                }
            }
        }
        return Collections.unmodifiableList(contracts);
    }

    ContractDefinition parseContractDefinition(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            ContractId id = new ContractId(root.get("id").asText());

            ContractVersion version = null;
            JsonNode versionNode = root.get("version");
            if (versionNode != null) {
                int major = versionNode.get("major").asInt();
                int minor = versionNode.get("minor").asInt();
                version = new ContractVersion(major, minor);
            }

            String name = getTextOrNull(root, "name");
            String description = getTextOrNull(root, "description");
            String inputSchemaRef = getTextOrNull(root, "inputSchemaRef");
            String outputSchemaRef = getTextOrNull(root, "outputSchemaRef");

            Map<String, Object> inputSchema = parseSchemaMap(root.get("inputSchema"));
            Map<String, Object> outputSchema = parseSchemaMap(root.get("outputSchema"));
            Map<String, Object> exampleResponse = parseSchemaMap(root.get("exampleResponse"));

            String promptInstruction = getTextOrNull(root, "promptInstruction");

            Map<String, String> metadata = new HashMap<>();
            JsonNode metadataNode = root.get("metadata");
            if (metadataNode != null && metadataNode.isObject()) {
                var fields = metadataNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    metadata.put(entry.getKey(), entry.getValue().asText());
                }
            }

            return new ContractDefinition(
                    id, version, name, description,
                    inputSchemaRef, outputSchemaRef,
                    inputSchema, outputSchema,
                    exampleResponse,
                    promptInstruction,
                    Collections.unmodifiableMap(metadata)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse contract definition: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchemaMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }
}
