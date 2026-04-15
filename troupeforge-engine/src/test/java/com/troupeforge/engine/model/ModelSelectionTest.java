package com.troupeforge.engine.model;

import com.troupeforge.core.id.TierId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelSelection;
import com.troupeforge.core.model.ModelTierDefinition;
import com.troupeforge.engine.model.ModelResolverImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Model Tier Selection")
class ModelSelectionTest {

    private ModelResolverImpl resolver;
    private ModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        resolver = new ModelResolverImpl();

        // Use LinkedHashMap to preserve order (TRIVIAL < SIMPLE < STANDARD < ADVANCED < COMPLEX < EXPERT)
        Map<TierId, ModelTierDefinition> tiers = new LinkedHashMap<>();
        tiers.put(new TierId("TRIVIAL"),
                new ModelTierDefinition(new TierId("TRIVIAL"), "haiku", 2048, 0.3, "Routing"));
        tiers.put(new TierId("SIMPLE"),
                new ModelTierDefinition(new TierId("SIMPLE"), "haiku", 8192, 0.5, "Greetings"));
        tiers.put(new TierId("STANDARD"),
                new ModelTierDefinition(new TierId("STANDARD"), "sonnet", 16384, 0.7, "Normal analysis"));
        tiers.put(new TierId("ADVANCED"),
                new ModelTierDefinition(new TierId("ADVANCED"), "sonnet", 32768, 0.7, "Multi-step"));
        tiers.put(new TierId("COMPLEX"),
                new ModelTierDefinition(new TierId("COMPLEX"), "opus", 32768, 0.7, "Architecture"));
        tiers.put(new TierId("EXPERT"),
                new ModelTierDefinition(new TierId("EXPERT"), "opus", 128000, 0.8, "Deep design"));

        Map<String, String> aliases = Map.of(
                "haiku", "claude-haiku-4-5-20251001",
                "sonnet", "claude-sonnet-4-6",
                "opus", "claude-opus-4-6"
        );

        modelConfig = new ModelConfig(aliases, tiers, "sonnet", 8192);
    }

    @Test
    void testRequestedTierUsedWhenAllowed() {
        List<TierId> allowed = List.of(
                new TierId("TRIVIAL"), new TierId("SIMPLE"), new TierId("STANDARD"));

        ModelSelection selection = resolver.resolve(allowed, modelConfig, new TierId("SIMPLE"));

        assertEquals("claude-haiku-4-5-20251001", selection.modelId());
        assertEquals(new TierId("SIMPLE"), selection.tier());
        assertEquals(8192, selection.maxTokens());
    }

    @Test
    void testFallsBackToNextHigherTier() {
        // Agent only allows STANDARD and ADVANCED, but request asks for SIMPLE
        List<TierId> allowed = List.of(new TierId("STANDARD"), new TierId("ADVANCED"));

        ModelSelection selection = resolver.resolve(allowed, modelConfig, new TierId("SIMPLE"));

        // Should fall back to the next higher tier that is allowed: STANDARD
        assertEquals("claude-sonnet-4-6", selection.modelId());
        assertEquals(new TierId("STANDARD"), selection.tier());
    }

    @Test
    void testFallsBackToFallbackModel() {
        // No allowed tiers match any configured tier
        List<TierId> allowed = List.of(new TierId("NONEXISTENT"));

        ModelSelection selection = resolver.resolve(allowed, modelConfig, new TierId("SIMPLE"));

        // Should fall back to the global fallback model
        assertEquals("claude-sonnet-4-6", selection.modelId());
        assertEquals(new TierId("FALLBACK"), selection.tier());
        assertEquals(8192, selection.maxTokens());
    }
}
