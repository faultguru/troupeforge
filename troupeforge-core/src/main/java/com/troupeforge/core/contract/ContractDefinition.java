package com.troupeforge.core.contract;

import java.util.*;

public record ContractDefinition(
    ContractId id,
    ContractVersion version,
    String name,
    String description,
    String inputSchemaRef,
    String outputSchemaRef,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    Map<String, Object> exampleResponse,
    String promptInstruction,
    Map<String, String> metadata
) {

    /**
     * Returns the effective input schema. If no input schema is defined,
     * defaults to a simple single-message string schema.
     */
    public Map<String, Object> effectiveInputSchema() {
        if (inputSchema != null && !inputSchema.isEmpty()) {
            return inputSchema;
        }
        return DEFAULT_INPUT_SCHEMA;
    }

    private static final Map<String, Object> DEFAULT_INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "message", Map.of(
                            "type", "string",
                            "description", "The user's chat message"
                    )
            ),
            "required", List.of("message")
    );
}
