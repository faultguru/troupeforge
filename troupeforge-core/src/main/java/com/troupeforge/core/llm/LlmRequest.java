package com.troupeforge.core.llm;

import com.troupeforge.core.id.AgentBucketId;

import java.util.*;

public record LlmRequest(
    AgentBucketId bucketId,
    String model,
    List<LlmMessage> messages,
    List<ToolDefinition> tools,
    double temperature,
    int maxTokens,
    Map<String, Object> metadata
) {
}
