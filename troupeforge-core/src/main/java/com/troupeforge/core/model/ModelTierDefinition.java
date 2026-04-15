package com.troupeforge.core.model;

import com.troupeforge.core.id.TierId;

public record ModelTierDefinition(TierId tier, String model, int maxTokens, double temperature, String description, String provider) {

    /**
     * Backward-compatible constructor without provider.
     */
    public ModelTierDefinition(TierId tier, String model, int maxTokens, double temperature, String description) {
        this(tier, model, maxTokens, temperature, description, null);
    }
}
