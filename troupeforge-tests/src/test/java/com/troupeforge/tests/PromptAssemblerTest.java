package com.troupeforge.tests;

import com.troupeforge.core.agent.AgentType;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.persona.PersonaDefinition;
import com.troupeforge.engine.prompt.PromptAssembler;
import com.troupeforge.tests.support.TestSpringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Prompt Assembler")
class PromptAssemblerTest {

    @Autowired
    private PromptAssembler promptAssembler;

    private RequestContext requestContext;

    @BeforeEach
    void setUp() {
        requestContext = new RequestContext(
                new RequestId("req-1"),
                new RequestorContext(new UserId("test-user"), new OrganizationId("test-org")),
                StageContext.LIVE,
                Instant.now()
        );
    }

    private AgentProfile buildProfile(String personaId, String displayName, List<PromptSection> sections) {
        AgentProfileId profileId = new AgentProfileId(new AgentId("test-agent"), new PersonaId(personaId));
        ResolvedAgent agent = new ResolvedAgent(
                new AgentId("test-agent"), "Test Agent", "desc",
                AgentType.WORKER, null, List.of(),
                Set.of(), Set.of(), Set.of(), Set.of(),
                sections, null, 1
        );
        PersonaDefinition persona = new PersonaDefinition(
                new PersonaId(personaId), personaId, displayName, null, "desc",
                null, Map.of(), List.of(), List.of(), List.of(), false, List.of()
        );
        return new AgentProfile(profileId, agent, persona, displayName, null, sections, List.of());
    }

    @Test
    @DisplayName("Identity prefix is included in assembled prompt")
    void testIdentityPrefixIncluded() {
        List<PromptSection> sections = List.of(
                new PromptSection("intro", List.of("Hello world"), 1)
        );
        AgentProfile profile = buildProfile("alice", "Alice", sections);

        String prompt = promptAssembler.assemble(requestContext, profile);

        assertTrue(prompt.startsWith("Your name is "),
                "Prompt should start with identity prefix");
        assertTrue(prompt.contains("Your persona ID is alice"),
                "Prompt should contain persona ID");
    }

    @Test
    @DisplayName("Identity uses displayName when available")
    void testIdentityUsesDisplayName() {
        List<PromptSection> sections = List.of(
                new PromptSection("intro", List.of("Section content"), 1)
        );
        AgentProfile profile = buildProfile("bob", "Bob the Builder", sections);

        String prompt = promptAssembler.assemble(requestContext, profile);

        assertTrue(prompt.contains("Your name is Bob the Builder"),
                "Prompt should use displayName: " + prompt);
    }

    @Test
    @DisplayName("Sections content appears in correct order in assembled prompt")
    void testSectionsInCorrectOrder() {
        List<PromptSection> sections = List.of(
                new PromptSection("first", List.of("FIRST_SECTION"), 10),
                new PromptSection("second", List.of("SECOND_SECTION"), 20),
                new PromptSection("third", List.of("THIRD_SECTION"), 30)
        );
        AgentProfile profile = buildProfile("charlie", "Charlie", sections);

        String prompt = promptAssembler.assemble(requestContext, profile);

        int firstIdx = prompt.indexOf("FIRST_SECTION");
        int secondIdx = prompt.indexOf("SECOND_SECTION");
        int thirdIdx = prompt.indexOf("THIRD_SECTION");

        assertTrue(firstIdx >= 0, "First section should be present");
        assertTrue(secondIdx >= 0, "Second section should be present");
        assertTrue(thirdIdx >= 0, "Third section should be present");
        assertTrue(firstIdx < secondIdx, "First section should appear before second");
        assertTrue(secondIdx < thirdIdx, "Second section should appear before third");
    }

    @Test
    @DisplayName("Empty sections produces empty prompt")
    void testEmptySectionsProducesIdentityOnly() {
        AgentProfile profile = buildProfile("empty", "Empty Agent", List.of());

        String prompt = promptAssembler.assemble(requestContext, profile);

        assertEquals("", prompt, "Empty sections should produce empty prompt");
    }
}
