package com.troupeforge.core.model;

import com.troupeforge.core.id.TierId;

import java.util.Map;

public record ModelConfig(Map<String, String> aliases, Map<TierId, ModelTierDefinition> tiers, String fallbackModel, int fallbackMaxTokens) {
}
