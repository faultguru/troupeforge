package com.troupeforge.engine.execution;

import com.troupeforge.core.llm.TokenUsage;
import java.util.concurrent.atomic.AtomicInteger;

public class CostAccumulator {
    private final AtomicInteger inputTokens = new AtomicInteger();
    private final AtomicInteger outputTokens = new AtomicInteger();
    private final AtomicInteger cacheReadTokens = new AtomicInteger();
    private final AtomicInteger cacheCreationTokens = new AtomicInteger();

    public void add(TokenUsage usage) {
        if (usage == null) return;
        inputTokens.addAndGet(usage.inputTokens());
        outputTokens.addAndGet(usage.outputTokens());
        cacheReadTokens.addAndGet(usage.cacheReadTokens());
        cacheCreationTokens.addAndGet(usage.cacheCreationTokens());
    }

    public TokenUsage total() {
        int in = inputTokens.get();
        int out = outputTokens.get();
        return new TokenUsage(in, out, in + out, cacheReadTokens.get(), cacheCreationTokens.get());
    }
}
