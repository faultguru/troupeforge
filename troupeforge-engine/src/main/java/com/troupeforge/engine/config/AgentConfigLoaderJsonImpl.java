package com.troupeforge.engine.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.agent.AgentDefinition;
import com.troupeforge.core.agent.AgentType;
import com.troupeforge.core.agent.DirectReturnPolicy;
import com.troupeforge.core.agent.InheritablePromptSections;
import com.troupeforge.core.agent.InheritableSet;
import com.troupeforge.core.agent.InheritanceAction;
import com.troupeforge.core.agent.PersonaSectionDefinition;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.CapabilityId;
import com.troupeforge.core.id.ContractCapabilityId;
import com.troupeforge.core.id.GuardrailId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.id.ToolId;
import com.troupeforge.core.persona.PersonaDefinition;
import com.troupeforge.core.persona.PersonaStyle;
import com.troupeforge.core.persona.Verbosity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentConfigLoaderJsonImpl implements AgentConfigLoader, PersonaConfigLoader {

    private static final String AGENTS_ROOT = "agents";
    private final ObjectMapper objectMapper;

    public AgentConfigLoaderJsonImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AgentDefinition> loadAgentDefinitions(OrgConfigSource configSource) {
        List<AgentDefinition> definitions = new ArrayList<>();
        loadAgentsRecursive(configSource, AGENTS_ROOT, definitions);
        return Collections.unmodifiableList(definitions);
    }

    @Override
    public Map<AgentId, List<PersonaDefinition>> loadPersonaDefinitions(OrgConfigSource configSource) {
        Map<AgentId, List<PersonaDefinition>> result = new HashMap<>();
        loadPersonasRecursive(configSource, AGENTS_ROOT, result);
        return Collections.unmodifiableMap(result);
    }

    private void loadAgentsRecursive(OrgConfigSource configSource, String parentPath,
                                      List<AgentDefinition> definitions) {
        // Scan the current directory for agent files (e.g. root-agent.json at agents/ level)
        List<String> parentFiles = configSource.listFiles(parentPath);
        for (String file : parentFiles) {
            if (file.endsWith("-agent.json")) {
                String content = configSource.readFile(file);
                if (content != null && !content.isBlank()) {
                    AgentDefinition def = parseAgentDefinition(content);
                    if (def != null) {
                        definitions.add(def);
                    }
                }
            }
        }

        // Recurse into subdirectories that contain agent definitions
        List<String> directories = configSource.listAgentDirectories(parentPath);
        for (String dir : directories) {
            loadAgentsRecursive(configSource, dir, definitions);
        }
    }

    private void loadPersonasRecursive(OrgConfigSource configSource, String parentPath,
                                        Map<AgentId, List<PersonaDefinition>> result) {
        // Check the current directory for agent + persona files
        loadPersonasForDirectory(configSource, parentPath, result);

        // Recurse into subdirectories
        List<String> directories = configSource.listAgentDirectories(parentPath);
        for (String dir : directories) {
            loadPersonasRecursive(configSource, dir, result);
        }
    }

    private void loadPersonasForDirectory(OrgConfigSource configSource, String dirPath,
                                           Map<AgentId, List<PersonaDefinition>> result) {
        List<String> files = configSource.listFiles(dirPath);
        AgentId agentId = null;
        for (String file : files) {
            if (file.endsWith("-agent.json")) {
                String content = configSource.readFile(file);
                if (content != null && !content.isBlank()) {
                    agentId = extractAgentId(content);
                }
            }
        }

        if (agentId != null) {
            String personasDir = dirPath + "/personas";
            List<String> personaFiles;
            try {
                personaFiles = configSource.listFiles(personasDir);
            } catch (Exception e) {
                personaFiles = Collections.emptyList();
            }
            List<PersonaDefinition> personas = new ArrayList<>();
            for (String personaFile : personaFiles) {
                if (personaFile.endsWith("-persona.json")) {
                    String content = configSource.readFile(personaFile);
                    if (content != null && !content.isBlank()) {
                        PersonaDefinition persona = parsePersonaDefinition(content);
                        if (persona != null) {
                            personas.add(persona);
                        }
                    }
                }
            }
            if (!personas.isEmpty()) {
                result.put(agentId, Collections.unmodifiableList(personas));
            }
        }
    }

    private AgentId extractAgentId(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode idNode = root.get("id");
            if (idNode != null) {
                return new AgentId(idNode.asText());
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    AgentDefinition parseAgentDefinition(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            AgentId id = new AgentId(root.get("id").asText());
            String name = getTextOrNull(root, "name");
            String description = getTextOrNull(root, "description");
            AgentType type = root.has("type")
                    ? AgentType.valueOf(root.get("type").asText())
                    : AgentType.WORKER;
            AgentId parent = root.has("parent")
                    ? new AgentId(root.get("parent").asText())
                    : null;

            InheritableSet<CapabilityId> capabilities = parseInheritableSet(root.get("capabilities"), CapabilityId::new);
            InheritableSet<GuardrailId> guardrails = parseInheritableSet(root.get("guardrails"), GuardrailId::new);
            InheritableSet<ToolId> tools = parseInheritableSet(root.get("tools"), ToolId::new);
            InheritableSet<ContractCapabilityId> contractCapabilities = parseInheritableSet(
                    root.get("contractCapabilities"), ContractCapabilityId::new);

            InheritablePromptSections promptSections = parsePromptSections(root.get("promptSections"));
            List<PersonaSectionDefinition> personaSections = parsePersonaSections(root.get("personaSections"));
            List<TierId> allowedTiers = parseTierList(root.get("allowedTiers"));

            DirectReturnPolicy directReturnPolicy = parseDirectReturnPolicy(root.get("directReturnPolicy"));
            int maxConcurrency = root.has("maxConcurrency") ? root.get("maxConcurrency").asInt() : 1;

            return new AgentDefinition(
                    id, name, description, type, parent,
                    capabilities, guardrails, tools, contractCapabilities,
                    promptSections, personaSections, allowedTiers,
                    directReturnPolicy, maxConcurrency
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse agent definition: " + e.getMessage(), e);
        }
    }

    private <T> InheritableSet<T> parseInheritableSet(JsonNode node, java.util.function.Function<String, T> mapper) {
        if (node == null) {
            return null;
        }
        InheritanceAction action = InheritanceAction.INHERIT;
        if (node.has("action") && !node.get("action").isNull()) {
            action = InheritanceAction.valueOf(node.get("action").asText());
        }
        List<T> values = new ArrayList<>();
        JsonNode valuesNode = node.get("values");
        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode v : valuesNode) {
                values.add(mapper.apply(v.asText()));
            }
        }
        return new InheritableSet<>(action, Collections.unmodifiableList(values));
    }

    private InheritablePromptSections parsePromptSections(JsonNode node) {
        if (node == null) {
            return null;
        }
        InheritanceAction action = InheritanceAction.INHERIT;
        if (node.has("action") && !node.get("action").isNull()) {
            action = InheritanceAction.valueOf(node.get("action").asText());
        }
        List<PromptSection> sections = new ArrayList<>();
        JsonNode sectionsNode = node.get("sections");
        if (sectionsNode != null && sectionsNode.isArray()) {
            for (JsonNode s : sectionsNode) {
                String key = s.get("key").asText();
                List<String> content = new ArrayList<>();
                JsonNode contentNode = s.get("content");
                if (contentNode != null && contentNode.isArray()) {
                    for (JsonNode c : contentNode) {
                        content.add(c.asText());
                    }
                }
                int order = s.has("order") ? s.get("order").asInt() : 0;
                sections.add(new PromptSection(key, Collections.unmodifiableList(content), order));
            }
        }
        return new InheritablePromptSections(action, Collections.unmodifiableList(sections));
    }

    private List<PersonaSectionDefinition> parsePersonaSections(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<PersonaSectionDefinition> result = new ArrayList<>();
        for (JsonNode s : node) {
            String key = s.get("key").asText();
            String description = getTextOrNull(s, "description");
            int order = s.has("order") ? s.get("order").asInt() : 0;
            boolean required = s.has("required") && s.get("required").asBoolean();
            result.add(new PersonaSectionDefinition(key, description, order, required));
        }
        return Collections.unmodifiableList(result);
    }

    private List<TierId> parseTierList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TierId> result = new ArrayList<>();
        for (JsonNode t : node) {
            result.add(new TierId(t.asText()));
        }
        return Collections.unmodifiableList(result);
    }

    private DirectReturnPolicy parseDirectReturnPolicy(JsonNode node) {
        if (node == null) {
            return null;
        }
        boolean enabled = node.has("enabled") && node.get("enabled").asBoolean();
        java.util.Set<ToolId> eligibleTools = new java.util.LinkedHashSet<>();
        JsonNode toolsNode = node.get("eligibleTools");
        if (toolsNode != null && toolsNode.isArray()) {
            for (JsonNode t : toolsNode) {
                eligibleTools.add(new ToolId(t.asText()));
            }
        }
        java.util.Set<ContractCapabilityId> eligibleContracts = new java.util.LinkedHashSet<>();
        JsonNode contractsNode = node.get("eligibleContracts");
        if (contractsNode != null && contractsNode.isArray()) {
            for (JsonNode c : contractsNode) {
                eligibleContracts.add(new ContractCapabilityId(c.asText()));
            }
        }
        return new DirectReturnPolicy(enabled,
                Collections.unmodifiableSet(eligibleTools),
                Collections.unmodifiableSet(eligibleContracts));
    }

    PersonaDefinition parsePersonaDefinition(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            PersonaId id = new PersonaId(root.get("id").asText());
            String name = getTextOrNull(root, "name");
            String displayName = getTextOrNull(root, "displayName");
            String avatar = getTextOrNull(root, "avatar");
            String description = getTextOrNull(root, "description");

            PersonaStyle style = parsePersonaStyle(root.get("style"));

            Map<String, List<String>> sections = new HashMap<>();
            JsonNode sectionsNode = root.get("sections");
            if (sectionsNode != null && sectionsNode.isObject()) {
                var fields = sectionsNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    List<String> content = new ArrayList<>();
                    if (entry.getValue().isArray()) {
                        for (JsonNode c : entry.getValue()) {
                            content.add(c.asText());
                        }
                    }
                    sections.put(entry.getKey(), Collections.unmodifiableList(content));
                }
            }

            List<String> additionalRules = parseStringList(root.get("additionalRules"));
            List<String> importantInstructions = parseStringList(root.get("importantInstructions"));
            List<TierId> allowedTiers = parseTierList(root.get("allowedTiers"));
            boolean disabled = root.has("disabled") && root.get("disabled").asBoolean();
            List<String> prefetchTools = parseStringList(root.get("prefetchTools"));

            return new PersonaDefinition(
                    id, name, displayName, avatar, description, style,
                    Collections.unmodifiableMap(sections),
                    additionalRules, importantInstructions, allowedTiers, disabled,
                    prefetchTools
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse persona definition: " + e.getMessage(), e);
        }
    }

    private PersonaStyle parsePersonaStyle(JsonNode node) {
        if (node == null) {
            return null;
        }
        String tone = getTextOrNull(node, "tone");
        Verbosity verbosity = node.has("verbosity") && !node.get("verbosity").isNull()
                ? Verbosity.valueOf(node.get("verbosity").asText())
                : null;
        String emoji = getTextOrNull(node, "emoji");
        String greeting = getTextOrNull(node, "greeting");
        return new PersonaStyle(tone, verbosity, emoji, greeting);
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return Collections.unmodifiableList(result);
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }
}
