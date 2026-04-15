package com.troupeforge.core.llm;

public record TokenUsage(int inputTokens, int outputTokens, int totalTokens, int cacheReadTokens, int cacheCreationTokens) {
}
