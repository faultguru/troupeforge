package com.troupeforge.core.llm;

import java.util.*;

public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
}
