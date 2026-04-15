package com.troupeforge.engine.execution;

import com.troupeforge.core.llm.FinishReason;
import com.troupeforge.core.llm.TokenUsage;
import java.time.Duration;

public sealed interface TraceEvent permits TraceEvent.LlmCall, TraceEvent.ToolExecution, TraceEvent.Delegation, TraceEvent.Error {
    record LlmCall(int iteration, String personaId, String model, Duration latency, TokenUsage usage, FinishReason finishReason) implements TraceEvent {}
    record ToolExecution(String toolName, Duration latency, boolean success, String error) implements TraceEvent {}
    record Delegation(String targetPersona, Duration latency, boolean success) implements TraceEvent {}
    record Error(String message, String cause) implements TraceEvent {}
}
