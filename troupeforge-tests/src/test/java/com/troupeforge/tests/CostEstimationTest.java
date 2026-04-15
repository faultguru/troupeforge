package com.troupeforge.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.llm.CostEstimate;
import com.troupeforge.core.llm.TokenUsage;
import com.troupeforge.infra.llm.ClaudeLlmProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CostEstimationTest {

    // Create provider with dummy config - estimateCost doesn't need real credentials
    private final ClaudeLlmProvider provider = new ClaudeLlmProvider(
            new ObjectMapper(), "http://localhost:9999", List.of());

    @Test
    void testOpusPricing() {
        TokenUsage usage = new TokenUsage(1_000_000, 1_000_000, 2_000_000, 0, 0);
        CostEstimate est = provider.estimateCost("claude-opus-4-6-20260327", usage);
        // Input: $15/MTok * 1M = $15, Output: $75/MTok * 1M = $75
        assertEquals(0, new BigDecimal("15.000000").compareTo(est.inputCost()));
        assertEquals(0, new BigDecimal("75.000000").compareTo(est.outputCost()));
        assertEquals(0, new BigDecimal("90.000000").compareTo(est.totalCost()));
        assertEquals("USD", est.currency());
    }

    @Test
    void testSonnetPricing() {
        TokenUsage usage = new TokenUsage(1_000_000, 1_000_000, 2_000_000, 0, 0);
        CostEstimate est = provider.estimateCost("claude-sonnet-4-6-20260327", usage);
        assertEquals(0, new BigDecimal("3.000000").compareTo(est.inputCost()));
        assertEquals(0, new BigDecimal("15.000000").compareTo(est.outputCost()));
    }

    @Test
    void testHaikuPricing() {
        TokenUsage usage = new TokenUsage(1_000_000, 1_000_000, 2_000_000, 0, 0);
        CostEstimate est = provider.estimateCost("claude-haiku-4-5-20251001", usage);
        assertEquals(0, new BigDecimal("0.250000").compareTo(est.inputCost()));
        assertEquals(0, new BigDecimal("1.250000").compareTo(est.outputCost()));
    }

    @Test
    void testSmallUsagePricing() {
        // 500 input tokens, 100 output tokens with Sonnet
        TokenUsage usage = new TokenUsage(500, 100, 600, 0, 0);
        CostEstimate est = provider.estimateCost("claude-sonnet-4-6-20260327", usage);
        // Input: $3/MTok * 500 = $0.001500, Output: $15/MTok * 100 = $0.001500
        assertEquals(0, new BigDecimal("0.001500").compareTo(est.inputCost()));
        assertEquals(0, new BigDecimal("0.001500").compareTo(est.outputCost()));
        assertEquals(0, new BigDecimal("0.003000").compareTo(est.totalCost()));
    }

    @Test
    void testUnknownModelUsesSonnetPricing() {
        TokenUsage usage = new TokenUsage(1_000_000, 1_000_000, 2_000_000, 0, 0);
        CostEstimate est = provider.estimateCost("gpt-4o", usage);
        // Defaults to Sonnet pricing
        assertEquals(0, new BigDecimal("3.000000").compareTo(est.inputCost()));
        assertEquals(0, new BigDecimal("15.000000").compareTo(est.outputCost()));
    }
}
