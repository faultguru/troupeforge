package com.troupeforge.core.persona;

import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.TierId;

import java.util.List;
import java.util.Map;

public record PersonaDefinition(
    PersonaId id,
    String name,
    String displayName,
    String avatar,
    String description,
    PersonaStyle style,
    Map<String, List<String>> sections,
    List<String> additionalRules,
    List<String> importantInstructions,
    List<TierId> allowedTiers,
    boolean disabled,
    List<String> prefetchTools
) {
    public PersonaDefinition {
        if (prefetchTools == null) {
            prefetchTools = List.of();
        }
    }
}
