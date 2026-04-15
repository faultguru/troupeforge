package com.troupeforge.core.llm;

import java.util.*;

public record ToolCall(String id, String name, Map<String, Object> arguments) {
}
