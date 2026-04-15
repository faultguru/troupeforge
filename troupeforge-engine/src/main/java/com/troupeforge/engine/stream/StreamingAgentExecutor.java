package com.troupeforge.engine.stream;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.RequestId;
import com.troupeforge.core.llm.LlmMessage;
import com.troupeforge.core.llm.LlmProvider;
import com.troupeforge.core.llm.LlmRequest;
import com.troupeforge.core.llm.LlmStreamEvent;
import com.troupeforge.core.llm.MessageContent;
import com.troupeforge.core.llm.MessageRole;
import com.troupeforge.core.llm.ToolDefinition;
import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.model.ModelSelection;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolInstruction;
import com.troupeforge.core.id.ToolId;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.model.ComplexityContext;
import com.troupeforge.engine.model.ModelSelectionService;
import com.troupeforge.engine.prompt.PromptAssembler;
import com.troupeforge.engine.session.AgentSessionFactory;
import com.troupeforge.engine.tool.ToolSchemaGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Streaming variant of the agent executor. Streams LLM response tokens
 * directly to the client for the first response turn (non-tool-use).
 * Falls back to non-streaming for tool-use iterations.
 */
public class StreamingAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(StreamingAgentExecutor.class);

    private final AgentBucketRegistry bucketRegistry;
    private final AgentSessionFactory sessionFactory;
    private final PromptAssembler promptAssembler;
    private final ModelSelectionService modelSelectionService;
    private final LlmProvider llmProvider;
    private final ContextStore contextStore;
    private final Map<String, Tool> toolRegistry;

    public StreamingAgentExecutor(AgentBucketRegistry bucketRegistry,
                                   AgentSessionFactory sessionFactory,
                                   PromptAssembler promptAssembler,
                                   ModelSelectionService modelSelectionService,
                                   LlmProvider llmProvider,
                                   ContextStore contextStore,
                                   Map<String, Tool> toolRegistry) {
        this.bucketRegistry = bucketRegistry;
        this.sessionFactory = sessionFactory;
        this.promptAssembler = promptAssembler;
        this.modelSelectionService = modelSelectionService;
        this.llmProvider = llmProvider;
        this.contextStore = contextStore;
        this.toolRegistry = toolRegistry;
    }

    public Flux<LlmStreamEvent> executeStream(RequestorContext requestor, StageContext stage,
                                                AgentProfileId targetAgent, String message,
                                                AgentSessionId resumeSessionId) {
        RequestContext requestContext = new RequestContext(
                RequestId.generate(), requestor, stage, Instant.now());

        AgentContext session;
        if (resumeSessionId != null) {
            try {
                session = sessionFactory.resumeSession(resumeSessionId);
            } catch (IllegalStateException e) {
                session = sessionFactory.newSession(requestContext, targetAgent);
            }
        } else {
            session = sessionFactory.newSession(requestContext, targetAgent);
        }

        AgentBucketId bucketId = session.bucketId();
        AgentBucket bucket = bucketRegistry.getBucket(bucketId);
        AgentProfile profile = bucket.agentProfiles().get(session.agentProfileId());
        if (profile == null) {
            return Flux.error(new IllegalStateException("Agent profile not found: " + session.agentProfileId().toKey()));
        }

        String systemPrompt = promptAssembler.assemble(requestContext, profile);

        // Collect tool instructions from assigned tools and append to prompt
        String toolInstructions = collectToolInstructions(profile);
        if (!toolInstructions.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + toolInstructions;
        }

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage(MessageRole.SYSTEM, List.of(new MessageContent.Text(systemPrompt))));

        List<LlmMessage> history = session.conversationHistory();
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        messages.add(new LlmMessage(MessageRole.USER, List.of(new MessageContent.Text(message))));

        ComplexityContext complexityCtx = new ComplexityContext(message, 1, Map.of());
        ModelSelection modelSelection = modelSelectionService.selectModel(profile, complexityCtx, bucket.modelConfig());

        List<ToolDefinition> toolDefinitions = buildToolDefinitions(profile);

        LlmRequest request = new LlmRequest(
                bucketId, modelSelection.modelId(), List.copyOf(messages),
                toolDefinitions, modelSelection.temperature(), modelSelection.maxTokens(), Map.of());

        log.info("Streaming LLM request: model={}, messages={}", request.model(), messages.size());

        return llmProvider.stream(request);
    }

    private List<ToolDefinition> buildToolDefinitions(AgentProfile profile) {
        var allowedTools = profile.agent().tools();
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : toolRegistry.values()) {
            if (allowedTools != null && !allowedTools.contains(new ToolId(tool.name()))) {
                continue;
            }
            Map<String, Object> schema = ToolSchemaGenerator.generateSchema(tool.requestType());
            definitions.add(new ToolDefinition(tool.name(), tool.description(), schema));
        }
        return definitions;
    }

    private String collectToolInstructions(AgentProfile profile) {
        var allowedTools = profile.agent().tools();
        Set<String> availableToolNames = new java.util.HashSet<>();
        List<ToolInstruction> instructions = new ArrayList<>();
        for (Tool tool : toolRegistry.values()) {
            if (allowedTools != null && !allowedTools.contains(new ToolId(tool.name()))) {
                continue;
            }
            availableToolNames.add(tool.name());
            ToolInstruction instruction = tool.toolInstruction();
            if (instruction != null) {
                instructions.add(instruction);
            }
        }
        instructions.sort(Comparator.comparingInt(ToolInstruction::priority));
        StringBuilder sb = new StringBuilder();
        for (ToolInstruction instruction : instructions) {
            String text = instruction.toPromptText(availableToolNames);
            if (!text.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(text);
            }
        }
        return sb.toString();
    }
}
