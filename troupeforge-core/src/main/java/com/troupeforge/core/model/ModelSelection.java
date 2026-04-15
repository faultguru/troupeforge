package com.troupeforge.core.model;

import com.troupeforge.core.id.TierId;

public record ModelSelection(String modelId, int maxTokens, double temperature, TierId tier, String description) {
}
