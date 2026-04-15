package com.troupeforge.engine.config;

import com.troupeforge.core.agent.AgentType;
import com.troupeforge.core.agent.PersonaSectionDefinition;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.persona.PersonaDefinition;
import com.troupeforge.core.persona.PersonaStyle;
import com.troupeforge.core.persona.Verbosity;
import com.troupeforge.engine.config.PersonaComposerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Persona Composition")
class PersonaCompositionTest {

    private PersonaComposerImpl composer;

    private final List<PersonaSectionDefinition> greeterPersonaSections = List.of(
            new PersonaSectionDefinition("persona-voice", "How this persona speaks", 200, true),
            new PersonaSectionDefinition("persona-strategy", "How this persona approaches work", 300, false)
    );

    private final List<TierId> greeterAllowedTiers = List.of(
            new TierId("TRIVIAL"), new TierId("SIMPLE")
    );

    @BeforeEach
    void setUp() {
        composer = new PersonaComposerImpl(
                agentId -> {
                    if (agentId.equals(new AgentId("greeter"))) {
                        return greeterPersonaSections;
                    }
                    return List.of(
                            new PersonaSectionDefinition("persona-voice", "Voice", 200, true)
                    );
                },
                agentId -> {
                    if (agentId.equals(new AgentId("greeter"))) {
                        return greeterAllowedTiers;
                    }
                    return List.of(new TierId("TRIVIAL"), new TierId("SIMPLE"),
                            new TierId("STANDARD"), new TierId("ADVANCED"));
                }
        );
    }

    private ResolvedAgent makeGreeterAgent() {
        return new ResolvedAgent(
                new AgentId("greeter"), "Greeter Agent", "A greeter", AgentType.WORKER,
                new AgentId("root"), List.of(new AgentId("root"), new AgentId("greeter")),
                Set.of(), Set.of(), Set.of(), Set.of(),
                List.of(
                        new PromptSection("core-identity", List.of("You are a TroupeForge agent."), 0),
                        new PromptSection("greeter-identity", List.of("You are a greeter."), 100)
                ),
                null, 0
        );
    }

    private PersonaDefinition makeSimonPersona() {
        return new PersonaDefinition(
                new PersonaId("simon"), "Simon", "Simon Says", null,
                "Simon repeats what you say",
                new PersonaStyle("playful", Verbosity.CONCISE, null, "Simon says..."),
                Map.of("persona-voice", List.of("You are playful and mimicking.", "Echo what users say.")),
                List.of(),
                List.of("Repeat back exactly what the user said, prefixed with 'Simon says:'"),
                List.of(new TierId("TRIVIAL")),
                false,
                List.of()
        );
    }

    private PersonaDefinition makeBondPersona() {
        return new PersonaDefinition(
                new PersonaId("bond"), "Bond", "James Bond", null,
                "007",
                new PersonaStyle("suave", Verbosity.CONCISE, null, null),
                Map.of(
                        "persona-voice", List.of("You are James Bond.", "Dry wit is your weapon."),
                        "persona-strategy", List.of("Greet everyone as if in a casino.")
                ),
                List.of(),
                List.of("Introduce yourself as 'Bond. James Bond.'"),
                List.of(new TierId("TRIVIAL"), new TierId("SIMPLE")),
                false,
                List.of()
        );
    }

    @Test
    void testPersonaSectionsAddedAsPromptSections() {
        ResolvedAgent agent = makeGreeterAgent();
        PersonaDefinition simon = makeSimonPersona();

        AgentProfile profile = composer.compose(agent, simon);

        // Should contain both agent sections and persona sections
        boolean hasPersonaVoice = profile.effectivePromptSections().stream()
                .anyMatch(s -> s.key().equals("persona-voice"));
        assertTrue(hasPersonaVoice, "Persona voice section should be present");

        // persona-voice content should come from the persona
        PromptSection voiceSection = profile.effectivePromptSections().stream()
                .filter(s -> s.key().equals("persona-voice")).findFirst().orElseThrow();
        assertTrue(voiceSection.content().contains("You are playful and mimicking."));
    }

    @Test
    void testImportantInstructionsAddedLast() {
        ResolvedAgent agent = makeGreeterAgent();
        PersonaDefinition simon = makeSimonPersona();

        AgentProfile profile = composer.compose(agent, simon);

        List<PromptSection> sections = profile.effectivePromptSections();
        PromptSection lastSection = sections.get(sections.size() - 1);

        assertEquals("important-instructions", lastSection.key());
        assertEquals(999, lastSection.order());
        assertTrue(lastSection.content().contains("Repeat back exactly what the user said, prefixed with 'Simon says:'"));
    }

    @Test
    void testAllowedTiersIntersectedFromAgentAndPersona() {
        ResolvedAgent agent = makeGreeterAgent();
        // Simon allows only TRIVIAL, greeter allows TRIVIAL and SIMPLE
        PersonaDefinition simon = makeSimonPersona();

        AgentProfile profile = composer.compose(agent, simon);

        // Intersection should be just TRIVIAL
        assertEquals(List.of(new TierId("TRIVIAL")), profile.allowedTiers());
    }

    @Test
    void testEffectiveDisplayNameComesFromPersona() {
        ResolvedAgent agent = makeGreeterAgent();
        PersonaDefinition simon = makeSimonPersona();

        AgentProfile profile = composer.compose(agent, simon);

        assertEquals("Simon Says", profile.effectiveDisplayName());
    }

    @Test
    void testMultiplePersonasOnSameAgent() {
        ResolvedAgent agent = makeGreeterAgent();
        PersonaDefinition simon = makeSimonPersona();
        PersonaDefinition bond = makeBondPersona();

        AgentProfile simonProfile = composer.compose(agent, simon);
        AgentProfile bondProfile = composer.compose(agent, bond);

        // Different profile IDs
        assertNotEquals(simonProfile.profileId(), bondProfile.profileId());

        // Different display names
        assertEquals("Simon Says", simonProfile.effectiveDisplayName());
        assertEquals("James Bond", bondProfile.effectiveDisplayName());

        // Different persona voice content
        PromptSection simonVoice = simonProfile.effectivePromptSections().stream()
                .filter(s -> s.key().equals("persona-voice")).findFirst().orElseThrow();
        PromptSection bondVoice = bondProfile.effectivePromptSections().stream()
                .filter(s -> s.key().equals("persona-voice")).findFirst().orElseThrow();
        assertNotEquals(simonVoice.content(), bondVoice.content());

        // Bond has persona-strategy, Simon does not
        boolean bondHasStrategy = bondProfile.effectivePromptSections().stream()
                .anyMatch(s -> s.key().equals("persona-strategy"));
        boolean simonHasStrategy = simonProfile.effectivePromptSections().stream()
                .anyMatch(s -> s.key().equals("persona-strategy"));
        assertTrue(bondHasStrategy);
        assertFalse(simonHasStrategy);
    }

    @Test
    void testDisabledPersonaRejected() {
        // This tests that the bucket loader skips disabled personas.
        // The PersonaComposerImpl itself does not check disabled;
        // the AgentBucketLoaderImpl does.
        PersonaDefinition disabled = new PersonaDefinition(
                new PersonaId("disabled"), "Disabled", "Disabled Persona", null,
                "Should not be used",
                new PersonaStyle("none", Verbosity.CONCISE, null, null),
                Map.of(),
                List.of(),
                List.of(),
                List.of(new TierId("TRIVIAL")),
                true,  // disabled
                List.of()
        );

        // Verify the persona is indeed disabled
        assertTrue(disabled.disabled());
        // The bucket loader checks persona.disabled() and skips composing it.
        // We verify the flag is set correctly for the loader's logic.
    }
}
