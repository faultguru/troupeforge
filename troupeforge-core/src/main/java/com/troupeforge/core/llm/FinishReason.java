package com.troupeforge.core.llm;

public enum FinishReason {
    STOP,
    TOOL_USE,
    MAX_TOKENS,
    ERROR
}
