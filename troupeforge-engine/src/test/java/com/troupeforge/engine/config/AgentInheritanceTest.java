package com.troupeforge.engine.config;

import com.troupeforge.core.agent.AgentDefinition;
import com.troupeforge.core.agent.AgentType;
import com.troupeforge.core.agent.InheritablePromptSections;
import com.troupeforge.core.agent.InheritableSet;
import com.troupeforge.core.agent.InheritanceAction;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.CapabilityId;
import com.troupeforge.core.id.GuardrailId;
import com.troupeforge.core.id.ToolId;
import com.troupeforge.engine.config.AgentInheritanceResolverImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent Inheritance Resolution")
class AgentInheritanceTest {

    private AgentInheritanceResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new AgentInheritanceResolverImpl();
    }

    private AgentDefinition rootAgent(String id,
                                       InheritableSet<CapabilityId> caps,
                                       InheritableSet<GuardrailId> guardrails,
                                       InheritableSet<ToolId> tools,
                                       InheritablePromptSections promptSections) {
        return new AgentDefinition(
                new AgentId(id), id, "Root " + id, AgentType.DISPATCHER,
                new AgentId(id),
                caps, guardrails, tools, null, promptSections, List.of(), List.of(), null, 0
        );
    }

    private AgentDefinition childAgent(String id, String parentId,
                                        InheritableSet<CapabilityId> caps,
                                        InheritableSet<GuardrailId> guardrails,
                                        InheritableSet<ToolId> tools,
                                        InheritablePromptSections promptSections) {
        return new AgentDefinition(
                new AgentId(id), id, "Child " + id, AgentType.WORKER,
                new AgentId(parentId),
                caps, guardrails, tools, null, promptSections, List.of(), List.of(), null, 0
        );
    }

    @Test
    void testRootAgentResolvesAsIs() {
        AgentDefinition root = rootAgent("root",
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new CapabilityId("routing"))),
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new GuardrailId("safety"))),
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new ToolId("delegate_to_agent"))),
                null);

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root));
        assertEquals(1, resolved.size());

        ResolvedAgent r = resolved.get(0);
        assertEquals(new AgentId("root"), r.id());
        assertTrue(r.capabilities().contains(new CapabilityId("routing")));
        assertTrue(r.guardrails().contains(new GuardrailId("safety")));
        assertTrue(r.tools().contains(new ToolId("delegate_to_agent")));
    }

    @Test
    void testChildInheritsParentCapabilities() {
        AgentDefinition root = rootAgent("root",
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new CapabilityId("routing"))),
                null, null, null);
        AgentDefinition child = childAgent("child", "root",
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new CapabilityId("conversation"))),
                null, null, null);

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        assertTrue(childResolved.capabilities().contains(new CapabilityId("routing")));
        assertTrue(childResolved.capabilities().contains(new CapabilityId("conversation")));
        assertEquals(2, childResolved.capabilities().size());
    }

    @Test
    void testChildReplacesParentCapabilities() {
        AgentDefinition root = rootAgent("root",
                new InheritableSet<>(InheritanceAction.INHERIT,
                        List.of(new CapabilityId("routing"), new CapabilityId("agentlist"))),
                null, null, null);
        AgentDefinition child = childAgent("child", "root",
                new InheritableSet<>(InheritanceAction.REPLACE, List.of(new CapabilityId("conversation"))),
                null, null, null);

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        assertEquals(Set.of(new CapabilityId("conversation")), childResolved.capabilities());
    }

    @Test
    void testChildRemovesParentCapabilities() {
        AgentDefinition root = rootAgent("root",
                new InheritableSet<>(InheritanceAction.INHERIT,
                        List.of(new CapabilityId("routing"), new CapabilityId("agentlist"))),
                null, null, null);
        AgentDefinition child = childAgent("child", "root",
                new InheritableSet<>(InheritanceAction.REMOVE, List.of(new CapabilityId("routing"))),
                null, null, null);

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        assertEquals(Set.of(new CapabilityId("agentlist")), childResolved.capabilities());
    }

    @Test
    void testNullInheritableSetInheritsEverything() {
        AgentDefinition root = rootAgent("root",
                new InheritableSet<>(InheritanceAction.INHERIT,
                        List.of(new CapabilityId("routing"), new CapabilityId("agentlist"))),
                null, null, null);
        AgentDefinition child = childAgent("child", "root", null, null, null, null);

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        assertEquals(Set.of(new CapabilityId("routing"), new CapabilityId("agentlist")),
                childResolved.capabilities());
    }

    @Test
    void testPromptSectionsInheritedAndSortedByOrder() {
        AgentDefinition root = rootAgent("root", null, null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("core-identity", List.of("You are root."), 0),
                        new PromptSection("core-safety", List.of("Be safe."), 10)
                )));
        AgentDefinition child = childAgent("child", "root", null, null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("child-identity", List.of("You are a child."), 100)
                )));

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        List<PromptSection> sections = childResolved.promptSections();
        assertEquals(3, sections.size());
        assertEquals("core-identity", sections.get(0).key());
        assertEquals("core-safety", sections.get(1).key());
        assertEquals("child-identity", sections.get(2).key());
    }

    @Test
    void testChildOverridesParentPromptSectionSameKey() {
        AgentDefinition root = rootAgent("root", null, null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("core-identity", List.of("Root identity"), 0)
                )));
        AgentDefinition child = childAgent("child", "root", null, null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("core-identity", List.of("Child identity"), 0)
                )));

        List<ResolvedAgent> resolved = resolver.resolve(List.of(root, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        assertEquals(1, childResolved.promptSections().size());
        assertEquals(List.of("Child identity"), childResolved.promptSections().get(0).content());
    }

    @Test
    void testDeepInheritanceChain() {
        AgentDefinition grandparent = rootAgent("gp",
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new CapabilityId("a"))),
                null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("gp-section", List.of("Grandparent says hi"), 0)
                )));
        AgentDefinition parent = childAgent("parent", "gp",
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new CapabilityId("b"))),
                null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("parent-section", List.of("Parent says hi"), 100)
                )));
        AgentDefinition child = childAgent("child", "parent",
                new InheritableSet<>(InheritanceAction.INHERIT, List.of(new CapabilityId("c"))),
                null, null,
                new InheritablePromptSections(InheritanceAction.INHERIT, List.of(
                        new PromptSection("child-section", List.of("Child says hi"), 200)
                )));

        List<ResolvedAgent> resolved = resolver.resolve(List.of(grandparent, parent, child));
        ResolvedAgent childResolved = resolved.stream()
                .filter(a -> a.id().equals(new AgentId("child"))).findFirst().orElseThrow();

        assertEquals(Set.of(new CapabilityId("a"), new CapabilityId("b"), new CapabilityId("c")),
                childResolved.capabilities());

        assertEquals(3, childResolved.promptSections().size());
        assertEquals("gp-section", childResolved.promptSections().get(0).key());
        assertEquals("parent-section", childResolved.promptSections().get(1).key());
        assertEquals("child-section", childResolved.promptSections().get(2).key());

        assertEquals(List.of(new AgentId("gp"), new AgentId("parent"), new AgentId("child")),
                childResolved.ancestorChain());
    }
}
