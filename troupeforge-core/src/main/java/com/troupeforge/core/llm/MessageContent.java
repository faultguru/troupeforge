package com.troupeforge.core.llm;

import java.util.*;

public sealed interface MessageContent permits MessageContent.Text, MessageContent.ToolUse, MessageContent.ToolResult {
    record Text(String text) implements MessageContent {}
    record ToolUse(String id, String name, Map<String, Object> arguments) implements MessageContent {}
    record ToolResult(String toolUseId, String content, boolean isError) implements MessageContent {}
}
