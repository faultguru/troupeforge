package com.troupeforge.core.llm;

import java.time.Duration;
import java.util.*;

public record LlmResponse(
    String content,
    FinishReason finishReason,
    TokenUsage usage,
    List<ToolCall> toolCalls,
    String model,
    Duration latency
) {
}
