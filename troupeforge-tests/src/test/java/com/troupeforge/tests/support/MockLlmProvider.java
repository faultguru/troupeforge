package com.troupeforge.tests.support;

import com.troupeforge.core.llm.CostEstimate;
import com.troupeforge.core.llm.FinishReason;
import com.troupeforge.core.llm.LlmProvider;
import com.troupeforge.core.llm.LlmRequest;
import com.troupeforge.core.llm.LlmResponse;
import com.troupeforge.core.llm.TokenUsage;
import com.troupeforge.core.llm.ToolCall;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class MockLlmProvider implements LlmProvider {

    private static final TokenUsage DEFAULT_USAGE = new TokenUsage(10, 20, 30, 0, 0);

    private final Queue<LlmResponse> responseQueue = new LinkedList<>();
    private final List<LlmRequest> requestHistory = new ArrayList<>();

    public void queueResponse(LlmResponse response) {
        responseQueue.add(response);
    }

    public void queueTextResponse(String text) {
        LlmResponse response = new LlmResponse(
                text,
                FinishReason.STOP,
                DEFAULT_USAGE,
                List.of(),
                "mock-model",
                Duration.ofMillis(1)
        );
        responseQueue.add(response);
    }

    public void queueToolCallResponse(String toolCallId, String toolName, Map<String, Object> args) {
        ToolCall toolCall = new ToolCall(toolCallId, toolName, args);
        LlmResponse response = new LlmResponse(
                null,
                FinishReason.TOOL_USE,
                DEFAULT_USAGE,
                List.of(toolCall),
                "mock-model",
                Duration.ofMillis(1)
        );
        responseQueue.add(response);
    }

    public record ToolCallSpec(String id, String name, Map<String, Object> args) {}

    public void queueMultiToolCallResponse(List<ToolCallSpec> specs) {
        List<ToolCall> toolCalls = specs.stream()
                .map(s -> new ToolCall(s.id(), s.name(), s.args()))
                .toList();
        LlmResponse response = new LlmResponse(
                null,
                FinishReason.TOOL_USE,
                DEFAULT_USAGE,
                toolCalls,
                "mock-model",
                Duration.ofMillis(1)
        );
        responseQueue.add(response);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requestHistory.add(request);
        LlmResponse response = responseQueue.poll();
        if (response == null) {
            throw new IllegalStateException(
                    "MockLlmProvider: no more queued responses. " +
                    "Queue a response before calling complete(). " +
                    "Total requests so far: " + requestHistory.size());
        }
        return response;
    }

    public LlmRequest getLastRequest() {
        if (requestHistory.isEmpty()) {
            throw new IllegalStateException("MockLlmProvider: no requests have been made yet.");
        }
        return requestHistory.get(requestHistory.size() - 1);
    }

    public List<LlmRequest> getRequestHistory() {
        return Collections.unmodifiableList(requestHistory);
    }

    public void reset() {
        responseQueue.clear();
        requestHistory.clear();
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean supports(String model) {
        return true;
    }

    @Override
    public CostEstimate estimateCost(String model, TokenUsage usage) {
        return new CostEstimate(
                model,
                usage,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD"
        );
    }
}
