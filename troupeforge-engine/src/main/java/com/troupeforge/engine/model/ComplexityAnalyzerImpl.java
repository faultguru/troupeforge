package com.troupeforge.engine.model;

import com.troupeforge.core.id.TierId;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link ComplexityAnalyzer} that uses a simple heuristic
 * based on the estimated scope of a task to determine the appropriate model tier.
 * <p>
 * If a "tier" hint is present in the complexity context's hints map, it is used
 * as an explicit override.
 */
public class ComplexityAnalyzerImpl implements ComplexityAnalyzer {

    private static final TierId SIMPLE = new TierId("SIMPLE");
    private static final TierId STANDARD = new TierId("STANDARD");
    private static final TierId ADVANCED = new TierId("ADVANCED");
    private static final TierId COMPLEX = new TierId("COMPLEX");

    private final List<TierId> availableTiers;

    /**
     * @param availableTiers the ordered list of tiers from lowest to highest capability
     */
    public ComplexityAnalyzerImpl(List<TierId> availableTiers) {
        this.availableTiers = availableTiers;
    }

    @Override
    public TierId analyze(ComplexityContext context) {
        // Check for explicit tier override in hints
        Map<String, Object> hints = context.hints();
        if (hints != null && hints.containsKey("tier")) {
            Object tierHint = hints.get("tier");
            if (tierHint instanceof String tierName) {
                return new TierId(tierName);
            } else if (tierHint instanceof TierId tierId) {
                return tierId;
            }
        }

        int scope = context.estimatedScope();

        if (availableTiers.isEmpty()) {
            return scopeToDefaultTier(scope);
        }

        if (scope <= 1) {
            return availableTiers.get(0);
        } else if (scope <= 3) {
            return availableTiers.size() > 1 ? availableTiers.get(1) : SIMPLE;
        } else if (scope <= 5) {
            return STANDARD;
        } else if (scope <= 8) {
            return availableTiers.size() > 2 ? availableTiers.get(availableTiers.size() - 2) : ADVANCED;
        } else {
            return availableTiers.get(availableTiers.size() - 1);
        }
    }

    private TierId scopeToDefaultTier(int scope) {
        if (scope <= 1) {
            return SIMPLE;
        } else if (scope <= 3) {
            return SIMPLE;
        } else if (scope <= 5) {
            return STANDARD;
        } else if (scope <= 8) {
            return ADVANCED;
        } else {
            return COMPLEX;
        }
    }
}
