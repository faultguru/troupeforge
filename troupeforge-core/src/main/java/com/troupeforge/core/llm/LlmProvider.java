package com.troupeforge.core.llm;

import reactor.core.publisher.Flux;

public interface LlmProvider {
    String name();
    boolean supports(String model);
    LlmResponse complete(LlmRequest request);
    default Flux<LlmStreamEvent> stream(LlmRequest request) {
        throw new UnsupportedOperationException("Streaming not supported by " + name());
    }
    CostEstimate estimateCost(String model, TokenUsage usage);
}
