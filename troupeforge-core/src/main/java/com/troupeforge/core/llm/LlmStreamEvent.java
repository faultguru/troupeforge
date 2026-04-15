package com.troupeforge.core.llm;

public sealed interface LlmStreamEvent permits LlmStreamEvent.ContentDelta, LlmStreamEvent.ToolCallDelta, LlmStreamEvent.Complete, LlmStreamEvent.StreamError {
    record ContentDelta(String text) implements LlmStreamEvent {}
    record ToolCallDelta(String toolCallId, String name, String argumentsChunk) implements LlmStreamEvent {}
    record Complete(LlmResponse response) implements LlmStreamEvent {}
    record StreamError(Throwable cause) implements LlmStreamEvent {}
}
