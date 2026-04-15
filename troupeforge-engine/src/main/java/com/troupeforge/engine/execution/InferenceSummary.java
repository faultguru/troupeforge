package com.troupeforge.engine.execution;

import com.troupeforge.core.llm.TokenUsage;

/**
 * Summary of a single LLM inference within a request.
 * Captures which persona made the call, what model was used, and token usage.
 */
public record InferenceSummary(
    String personaId,
    String model,
    long latencyMs,
    TokenUsage usage
) {}
