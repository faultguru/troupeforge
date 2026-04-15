package com.troupeforge.engine.model;

import com.troupeforge.core.id.TierId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelSelection;
import com.troupeforge.core.model.ModelTierDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link ModelResolver} that selects a model tier based on
 * allowed tiers, global config, and a requested tier. Falls back to the closest
 * available tier or the global fallback model.
 */
public class ModelResolverImpl implements ModelResolver {

    @Override
    public ModelSelection resolve(List<TierId> allowedTiers, ModelConfig globalConfig, TierId requestedTier) {
        Map<TierId, ModelTierDefinition> tierDefs = globalConfig.tiers();

        // If requestedTier is allowed and exists in config, use it directly
        if (allowedTiers.contains(requestedTier) && tierDefs.containsKey(requestedTier)) {
            return toSelection(tierDefs.get(requestedTier), globalConfig);
        }

        // Find the closest tier in allowedTiers that exists in globalConfig
        // Build a list of valid tiers (both allowed and configured), ordered by their
        // position in the tiers map to determine "closeness"
        List<TierId> configuredOrder = new ArrayList<>(tierDefs.keySet());
        int requestedIndex = configuredOrder.indexOf(requestedTier);

        List<TierId> validTiers = new ArrayList<>();
        for (TierId tier : allowedTiers) {
            if (tierDefs.containsKey(tier)) {
                validTiers.add(tier);
            }
        }

        if (validTiers.isEmpty()) {
            return fallback(globalConfig);
        }

        if (requestedIndex < 0) {
            // Requested tier not in config at all; pick the first valid tier
            return toSelection(tierDefs.get(validTiers.get(0)), globalConfig);
        }

        // Prefer next higher tier, fall back to next lower
        TierId best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean bestIsHigher = false;

        for (TierId candidate : validTiers) {
            int candidateIndex = configuredOrder.indexOf(candidate);
            int distance = candidateIndex - requestedIndex;
            boolean isHigher = distance >= 0;
            int absDistance = Math.abs(distance);

            if (best == null) {
                best = candidate;
                bestDistance = absDistance;
                bestIsHigher = isHigher;
            } else if (isHigher && !bestIsHigher) {
                // Prefer higher over lower
                best = candidate;
                bestDistance = absDistance;
                bestIsHigher = true;
            } else if (isHigher == bestIsHigher && absDistance < bestDistance) {
                // Same direction, closer distance
                best = candidate;
                bestDistance = absDistance;
                bestIsHigher = isHigher;
            }
        }

        if (best != null) {
            return toSelection(tierDefs.get(best), globalConfig);
        }

        return fallback(globalConfig);
    }

    private ModelSelection toSelection(ModelTierDefinition tierDef, ModelConfig globalConfig) {
        String modelId = resolveAlias(tierDef.model(), globalConfig.aliases());
        return new ModelSelection(modelId, tierDef.maxTokens(), tierDef.temperature(), tierDef.tier(), tierDef.description());
    }

    private String resolveAlias(String model, Map<String, String> aliases) {
        if (aliases != null && aliases.containsKey(model)) {
            return aliases.get(model);
        }
        return model;
    }

    private ModelSelection fallback(ModelConfig globalConfig) {
        String modelId = resolveAlias(globalConfig.fallbackModel(), globalConfig.aliases());
        return new ModelSelection(modelId, globalConfig.fallbackMaxTokens(), 0.7, new TierId("FALLBACK"), "Fallback model");
    }
}
