package com.troupeforge.tests;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.agent.AgentType;
import com.troupeforge.core.agent.DirectReturnPolicy;
import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.agent.ResolvedAgent;
import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.OrganizationId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.id.ToolId;
import com.troupeforge.core.id.UserId;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelTierDefinition;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.persona.PersonaDefinition;
import com.troupeforge.core.persona.PersonaStyle;
import com.troupeforge.core.persona.Verbosity;
import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.execution.AgentExecutorImpl;
import com.troupeforge.engine.model.ComplexityAnalyzerImpl;
import com.troupeforge.engine.model.ModelResolverImpl;
import com.troupeforge.engine.model.ModelSelectionServiceImpl;
import com.troupeforge.engine.prompt.PromptAssemblerImpl;
import com.troupeforge.engine.session.AgentSessionFactoryImpl;
import com.troupeforge.infra.storage.InMemoryContextStore;
import com.troupeforge.tests.support.MockLlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Direct Return (Skip Inference)")
class DirectReturnTest {

    private MockLlmProvider mockLlm;
    private InMemoryContextStore contextStore;
    private AgentExecutorImpl executor;
    private AgentSessionFactoryImpl sessionFactory;

    private static final AgentId AGENT_ID = new AgentId("tool-agent");
    private static final PersonaId PERSONA_ID = new PersonaId("default");
    private static final AgentProfileId PROFILE_ID = new AgentProfileId(AGENT_ID, PERSONA_ID);
    private static final OrganizationId ORG = new OrganizationId("test-org");
    private static final StageContext STAGE = StageContext.LIVE;
    private static final AgentBucketId BUCKET_ID = AgentBucketId.of(ORG, STAGE);

    // --- Typed test tools ---

    static class LookupDataTool implements Tool {
        public record Request(@ToolParam(description = "The query to look up") String query) {}
        public record Response(String result) {}

        @Override public String name() { return "lookup_data"; }
        @Override public String description() { return "Look up data"; }
        @Override public Class<Request> requestType() { return Request.class; }
        @Override public Class<Response> responseType() { return Response.class; }
        @Override public Record execute(ToolContext context, Record request) {
            var req = (Request) request;
            return new Response("Looked up: " + req.query());
        }
    }

    static class OtherTool implements Tool {
        public record Request(@ToolParam(description = "Input data", required = false) String input) {}
        public record Response(String result) {}

        @Override public String name() { return "other_tool"; }
        @Override public String description() { return "Another tool"; }
        @Override public Class<Request> requestType() { return Request.class; }
        @Override public Class<Response> responseType() { return Response.class; }
        @Override public Record execute(ToolContext context, Record request) {
            return new Response("Other result");
        }
    }

    @BeforeEach
    void setUp() {
        mockLlm = new MockLlmProvider();
        contextStore = new InMemoryContextStore();
        sessionFactory = new AgentSessionFactoryImpl(contextStore);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, Tool> toolRegistry = Map.of(
                "lookup_data", new LookupDataTool(),
                "other_tool", new OtherTool()
        );

        ModelConfig modelConfig = new ModelConfig(
                Map.of(),
                Map.of(new TierId("SIMPLE"),
                        new ModelTierDefinition(new TierId("SIMPLE"), "mock", 4096, 0.7, "Simple")),
                "mock", 4096);

        var modelSelectionService = new ModelSelectionServiceImpl(
                new ModelResolverImpl(),
                new ComplexityAnalyzerImpl(List.of(new TierId("SIMPLE"))),
                modelConfig);

        DirectReturnPolicy directReturnPolicy = new DirectReturnPolicy(
                true,
                Set.of(new ToolId("lookup_data")),
                Set.of()
        );

        ResolvedAgent agent = new ResolvedAgent(
                AGENT_ID, "Tool Agent", "Test", AgentType.WORKER,
                AGENT_ID, List.of(AGENT_ID),
                Set.of(), Set.of(), Set.of(new ToolId("lookup_data"), new ToolId("other_tool")),
                Set.of(),
                List.of(new PromptSection("identity", List.of("You are a test agent."), 0)),
                directReturnPolicy, 0
        );

        PersonaDefinition persona = new PersonaDefinition(
                PERSONA_ID, "Default", "Default", null, "Default",
                new PersonaStyle("neutral", Verbosity.CONCISE, null, null),
                Map.of(), List.of(), List.of(),
                List.of(new TierId("SIMPLE")), false,
                List.of()
        );

        AgentProfile profile = new AgentProfile(
                PROFILE_ID, agent, persona, "Default", "",
                agent.promptSections(), List.of(new TierId("SIMPLE"))
        );

        AgentBucket bucket = new AgentBucket(
                BUCKET_ID, ORG, STAGE,
                Map.of(AGENT_ID, agent),
                Map.of(PROFILE_ID, profile),
                modelConfig, List.of(), Instant.now(), "test-v1"
        );

        AgentBucketRegistry bucketRegistry = new AgentBucketRegistry() {
            @Override public AgentBucket getBucket(AgentBucketId id) { return bucket; }
            @Override public void loadBucket(AgentBucketId id, OrganizationId org, StageContext stage,
                                             com.troupeforge.core.bucket.OrgConfigSource src) {}
            @Override public void reloadBucket(AgentBucketId id) {}
            @Override public void unloadBucket(AgentBucketId id) {}
            @Override public Set<AgentBucketId> activeBuckets() { return Set.of(BUCKET_ID); }
        };

        executor = new AgentExecutorImpl(
                bucketRegistry, sessionFactory, new PromptAssemblerImpl(),
                modelSelectionService, mockLlm, contextStore, toolRegistry, objectMapper
        );
    }

    private RequestContext makeRequestContext() {
        return new RequestContext(
                RequestId.generate(),
                new RequestorContext(new UserId("test-user"), ORG),
                STAGE, Instant.now()
        );
    }

    @Test
    void testDirectReturnSkipsResponseInference() {
        mockLlm.queueToolCallResponse("tc-1", "lookup_data", Map.of("query", "test-query"));
        mockLlm.queueTextResponse("Here is the result: Looked up: test-query");

        RequestContext reqCtx = makeRequestContext();
        AgentContext session = sessionFactory.newSession(reqCtx, PROFILE_ID);

        String result = executor.execute(reqCtx, session.sessionId(), "Look up test-query").join().response();

        assertEquals("Here is the result: Looked up: test-query", result);
        assertEquals(2, mockLlm.getRequestHistory().size(),
                "Tool call should be processed and result fed back for response inference");
    }

    @Test
    void testNonEligibleToolContinuesLoop() {
        mockLlm.queueToolCallResponse("tc-1", "other_tool", Map.of("input", "data"));
        mockLlm.queueTextResponse("Final response after tool use");

        RequestContext reqCtx = makeRequestContext();
        AgentContext session = sessionFactory.newSession(reqCtx, PROFILE_ID);

        String result = executor.execute(reqCtx, session.sessionId(), "Use other tool").join().response();

        assertEquals("Final response after tool use", result);
        assertEquals(2, mockLlm.getRequestHistory().size(),
                "Non-eligible tool should continue the loop for response inference");
    }
}
