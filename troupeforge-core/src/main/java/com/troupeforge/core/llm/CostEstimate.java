package com.troupeforge.core.llm;

import java.math.BigDecimal;

public record CostEstimate(String model, TokenUsage usage, BigDecimal inputCost, BigDecimal outputCost, BigDecimal totalCost, String currency) {
}
