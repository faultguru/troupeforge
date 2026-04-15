package com.troupeforge.engine.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.agent.DirectReturnPolicy;
import com.troupeforge.core.agent.LoopAction;
import com.troupeforge.core.context.AgentContext;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.AgentSessionId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.core.id.ToolId;
import com.troupeforge.core.llm.FinishReason;
import com.troupeforge.core.llm.LlmMessage;
import com.troupeforge.core.llm.LlmProvider;
import com.troupeforge.core.llm.LlmRequest;
import com.troupeforge.core.llm.LlmResponse;
import com.troupeforge.core.llm.MessageContent;
import com.troupeforge.core.llm.MessageRole;
import com.troupeforge.core.llm.ToolCall;
import com.troupeforge.core.llm.ToolDefinition;
import com.troupeforge.core.model.ModelSelection;
import com.troupeforge.core.model.ModelTierDefinition;
import com.troupeforge.core.model.ProviderConfig;
import com.troupeforge.core.persona.AgentProfile;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolInstruction;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.model.ComplexityContext;
import com.troupeforge.core.llm.LlmProviderFactory;
import com.troupeforge.engine.model.ModelSelectionService;
import com.troupeforge.engine.prompt.PromptAssembler;
import com.troupeforge.engine.session.AgentSessionFactory;
import com.troupeforge.engine.tool.ToolSchemaGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AgentExecutorImpl implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutorImpl.class);

    private static final int MAX_LOOP_ITERATIONS = 10;
    private static final String DELEGATE_TOOL_NAME = "delegate_to_agent";
    private static final String HANDOVER_TOOL_NAME = "handover_to_agent";

    private final AgentBucketRegistry bucketRegistry;
    private final AgentSessionFactory sessionFactory;
    private final PromptAssembler promptAssembler;
    private final ModelSelectionService modelSelectionService;
    private final LlmProvider llmProvider;
    private final List<LlmProviderFactory> providerFactories;
    private final ContextStore contextStore;
    private final Map<String, Tool> toolRegistry;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, LlmProvider> providerCache = new ConcurrentHashMap<>();

    private static class ExecContext {
        final List<TraceEvent> traceEvents = Collections.synchronizedList(new ArrayList<>());
        final CostAccumulator costAccumulator = new CostAccumulator();
        final Instant startedAt = Instant.now();
        volatile Set<ToolId> allowedTools; // set once after profile is loaded, read from parallel threads
    }

    /**
     * Backward-compatible constructor using a single LlmProvider.
     */
    public AgentExecutorImpl(AgentBucketRegistry bucketRegistry,
                                AgentSessionFactory sessionFactory,
                                PromptAssembler promptAssembler,
                                ModelSelectionService modelSelectionService,
                                LlmProvider llmProvider,
                                ContextStore contextStore,
                                Map<String, Tool> toolRegistry,
                                ObjectMapper objectMapper) {
        this(bucketRegistry, sessionFactory, promptAssembler, modelSelectionService,
             llmProvider, List.of(), contextStore, toolRegistry, objectMapper);
    }

    public AgentExecutorImpl(AgentBucketRegistry bucketRegistry,
                                AgentSessionFactory sessionFactory,
                                PromptAssembler promptAssembler,
                                ModelSelectionService modelSelectionService,
                                LlmProvider llmProvider,
                                List<LlmProviderFactory> providerFactories,
                                ContextStore contextStore,
                                Map<String, Tool> toolRegistry,
                                ObjectMapper objectMapper) {
        this.bucketRegistry = Objects.requireNonNull(bucketRegistry);
        this.sessionFactory = Objects.requireNonNull(sessionFactory);
        this.promptAssembler = Objects.requireNonNull(promptAssembler);
        this.modelSelectionService = Objects.requireNonNull(modelSelectionService);
        this.llmProvider = Objects.requireNonNull(llmProvider);
        this.providerFactories = Objects.requireNonNull(providerFactories);
        this.contextStore = Objects.requireNonNull(contextStore);
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        log.info("AgentExecutorImpl initialized with {} tools: {}", toolRegistry.size(), toolRegistry.keySet());
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(RequestContext requestContext, AgentSessionId sessionId,
                                             String message) {
        return CompletableFuture.supplyAsync(() -> executeSync(requestContext, sessionId, message));
    }

    private ExecutionResult executeSync(RequestContext requestContext, AgentSessionId sessionId, String message) {
        MDC.put("requestId", requestContext.requestId().value());
        MDC.put("sessionId", sessionId.value());
        try {
            return executeSyncInner(requestContext, sessionId, message);
        } finally {
            MDC.remove("requestId");
            MDC.remove("sessionId");
        }
    }

    private ExecutionResult executeSyncInner(RequestContext requestContext, AgentSessionId sessionId, String message) {
        log.debug("Execute message content: {}", message);

        ExecContext execCtx = new ExecContext();

        AgentContext agentContext = contextStore.load(sessionId)
            .orElseThrow(() -> new IllegalStateException("No agent context for session: " + sessionId.value()));

        AgentBucketId bucketId = agentContext.bucketId();
        AgentBucket bucket = bucketRegistry.getBucket(bucketId);
        AgentProfile profile = bucket.agentProfiles().get(agentContext.agentProfileId());
        if (profile == null) {
            throw new IllegalStateException(
                "Agent profile not found: " + agentContext.agentProfileId().toKey());
        }

        var agentTools = profile.agent().tools();
        // null means "all tools allowed", empty set means "no tools allowed"
        execCtx.allowedTools = agentTools;

        log.info("Execute started: sessionId={}, profileId={}", sessionId.value(), agentContext.agentProfileId().toKey());

        String systemPrompt = promptAssembler.assemble(requestContext, profile);

        // Collect tool instructions from assigned tools and append to prompt
        String toolInstructions = collectToolInstructions(profile);
        if (!toolInstructions.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + toolInstructions;
        }

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage(MessageRole.SYSTEM,
            List.of(new MessageContent.Text(systemPrompt))));

        // Load conversation history from previous turns
        List<LlmMessage> history = agentContext.conversationHistory();
        if (history != null && !history.isEmpty()) {
            log.info("Loaded conversation history: {} messages", history.size());
            messages.addAll(history);
        }

        // Execute prefetch tools if configured and no prior conversation history
        if (history == null || history.isEmpty()) {
            executePrefetchTools(profile, messages, requestContext, agentContext, execCtx);
        }

        messages.add(new LlmMessage(MessageRole.USER,
            List.of(new MessageContent.Text(message))));

        ComplexityContext complexityCtx = new ComplexityContext(message, 1, Map.of());
        ModelSelection modelSelection = modelSelectionService.selectModel(profile, complexityCtx, bucket.modelConfig());

        // Resolve the LLM provider for this request
        LlmProvider effectiveProvider = resolveProvider(modelSelection, bucket);

        List<ToolDefinition> toolDefinitions = buildToolDefinitions(profile);

        for (int iteration = 0; iteration < MAX_LOOP_ITERATIONS; iteration++) {
            LlmRequest request = new LlmRequest(
                bucketId,
                modelSelection.modelId(),
                List.copyOf(messages),
                toolDefinitions,
                modelSelection.temperature(),
                modelSelection.maxTokens(),
                Map.of()
            );

            log.info("LLM call iteration {}: model={}, messageCount={}, toolCount={}",
                    iteration + 1, request.model(),
                    request.messages() != null ? request.messages().size() : 0,
                    request.tools() != null ? request.tools().size() : 0);

            Instant llmStart = Instant.now();
            LlmResponse response = effectiveProvider.complete(request);
            Duration llmLatency = Duration.between(llmStart, Instant.now());

            execCtx.traceEvents.add(new TraceEvent.LlmCall(
                iteration + 1,
                agentContext.agentProfileId().personaId().value(),
                response.model() != null ? response.model() : request.model(),
                llmLatency,
                response.usage(),
                response.finishReason()
            ));
            execCtx.costAccumulator.add(response.usage());

            LoopAction action = determineAction(response);
            log.info("LLM response: action={}, finishReason={}", action, response.finishReason());

            switch (action) {
                case RESPOND -> {
                    log.info("Execute completed: sessionId={}", sessionId.value());
                    String content = response.content() != null ? response.content() : "";
                    addAssistantMessage(messages, response);
                    saveConversationHistory(agentContext, messages);
                    saveContext(agentContext);
                    return buildResult(content, agentContext, requestContext, sessionId, execCtx);
                }

                case DIRECT_RETURN -> {
                    String directResult = processToolCallsForDirectReturn(
                        response, requestContext, agentContext, profile, execCtx);
                    if (directResult != null) {
                        saveConversationHistory(agentContext, messages);
                        saveContext(agentContext);
                        return buildResult(directResult, agentContext, requestContext, sessionId, execCtx);
                    }
                    addToolResultMessages(messages, response, requestContext, agentContext, execCtx);
                }

                case DELEGATE_WAIT -> {
                    addAssistantMessage(messages, response);
                    executeToolCallsInParallel(messages, response.toolCalls(), requestContext, agentContext, execCtx);
                }

                case HANDOVER -> {
                    for (ToolCall toolCall : response.toolCalls()) {
                        if (HANDOVER_TOOL_NAME.equals(toolCall.name())) {
                            ExecutionResult handoverResult = handleHandover(
                                toolCall, requestContext, agentContext, execCtx);
                            saveConversationHistory(agentContext, messages);
                            saveContext(agentContext);
                            return handoverResult;
                        }
                    }
                    addToolResultMessages(messages, response, requestContext, agentContext, execCtx);
                }

                case CONTINUE -> {
                    addToolResultMessages(messages, response, requestContext, agentContext, execCtx);
                }
            }
        }

        log.warn("Max loop iterations reached: sessionId={}", sessionId.value());
        saveConversationHistory(agentContext, messages);
        saveContext(agentContext);
        return buildResult("[Max loop iterations reached]", agentContext, requestContext, sessionId, execCtx);
    }

    private void executePrefetchTools(AgentProfile profile, List<LlmMessage> messages,
                                       RequestContext requestContext, AgentContext agentContext,
                                       ExecContext execCtx) {
        List<String> prefetchToolNames = profile.persona().prefetchTools();
        if (prefetchToolNames == null || prefetchToolNames.isEmpty()) {
            return;
        }

        for (String toolName : prefetchToolNames) {
            Tool tool = toolRegistry.get(toolName);
            if (tool == null) {
                log.warn("Prefetch tool not found: '{}'", toolName);
                continue;
            }

            // Runtime guardrail: verify the tool is allowed for this agent
            if (execCtx.allowedTools != null && !execCtx.allowedTools.contains(new ToolId(toolName))) {
                log.warn("Prefetch tool '{}' not in allowed set for agent, skipping", toolName);
                continue;
            }

            log.info("Executing prefetch tool: {}", toolName);

            ToolContext toolContext = new ToolContext(
                requestContext,
                agentContext.sessionId(),
                agentContext.agentProfileId(),
                Path.of("."),
                Map.of()
            );

            try {
                // Create a default request with all-null fields for parameterless tools
                var constructor = tool.requestType().getDeclaredConstructors()[0];
                Object[] args = new Object[constructor.getParameterCount()];
                Record request = (Record) constructor.newInstance(args);

                Record response = tool.execute(toolContext, request);
                if (response == null) {
                    log.warn("Prefetch tool '{}' returned null, skipping", toolName);
                    continue;
                }

                String resultJson = objectMapper.writeValueAsString(response);
                String syntheticId = "prefetch-" + toolName;

                // Add as assistant tool_use message
                messages.add(new LlmMessage(MessageRole.ASSISTANT,
                    List.of(new MessageContent.ToolUse(syntheticId, toolName, Map.of()))));

                // Add as tool result message
                messages.add(new LlmMessage(MessageRole.TOOL,
                    List.of(new MessageContent.ToolResult(syntheticId, resultJson, false))));

                log.info("Prefetch tool '{}' result injected into context", toolName);
            } catch (Exception e) {
                log.error("Prefetch tool '{}' execution failed: {}", toolName, e.getMessage(), e);
            }
        }
    }

    private ExecutionResult buildResult(String content, AgentContext agentContext,
                                         RequestContext requestContext, AgentSessionId sessionId,
                                         ExecContext execCtx) {
        Instant completedAt = Instant.now();
        ExecutionTrace trace = new ExecutionTrace(
            requestContext.requestId(),
            sessionId,
            agentContext.agentProfileId(),
            List.copyOf(execCtx.traceEvents),
            execCtx.startedAt,
            completedAt,
            execCtx.costAccumulator.total(),
            Duration.between(execCtx.startedAt, completedAt)
        );
        // Build flat inference list from all LlmCall trace events (including merged child events)
        List<InferenceSummary> inferences = execCtx.traceEvents.stream()
                .filter(e -> e instanceof TraceEvent.LlmCall)
                .map(e -> {
                    var call = (TraceEvent.LlmCall) e;
                    return new InferenceSummary(
                            call.personaId(), call.model(),
                            call.latency().toMillis(), call.usage());
                })
                .toList();
        return new ExecutionResult(content, agentContext.agentProfileId(), trace, execCtx.costAccumulator.total(), inferences);
    }

    /**
     * Merges child execution's LlmCall trace events into the parent's trace,
     * so the final inference list includes all calls across delegation chains.
     */
    private void mergeChildTraceEvents(ExecutionResult childResult, ExecContext parentExecCtx) {
        if (childResult.trace() != null && childResult.trace().events() != null) {
            for (TraceEvent event : childResult.trace().events()) {
                if (event instanceof TraceEvent.LlmCall) {
                    parentExecCtx.traceEvents.add(event);
                }
            }
        }
    }

    private LlmProvider resolveProvider(ModelSelection modelSelection, AgentBucket bucket) {
        if (providerFactories.isEmpty() || bucket.providerConfigs().isEmpty()) {
            return llmProvider;
        }

        // Look up the provider id from the tier definition
        ModelTierDefinition tierDef = bucket.modelConfig().tiers().get(modelSelection.tier());
        if (tierDef == null || tierDef.provider() == null) {
            return llmProvider;
        }

        String providerId = tierDef.provider();
        return providerCache.computeIfAbsent(providerId, id -> {
            ProviderConfig providerConfig = bucket.providerConfigs().get(id);
            if (providerConfig == null) {
                log.warn("Provider config not found for id={}, falling back to default provider", id);
                return llmProvider;
            }

            for (LlmProviderFactory factory : providerFactories) {
                if (factory.supports(providerConfig.type())) {
                    log.info("Creating LLM provider from config: id={}, type={}", id, providerConfig.type());
                    return factory.create(providerConfig);
                }
            }

            log.warn("No factory supports provider type={}, falling back to default provider", providerConfig.type());
            return llmProvider;
        });
    }

    private LoopAction determineAction(LlmResponse response) {
        if (response.finishReason() == FinishReason.STOP || response.finishReason() == FinishReason.MAX_TOKENS) {
            return LoopAction.RESPOND;
        }

        if (response.finishReason() == FinishReason.TOOL_USE && response.toolCalls() != null) {
            for (ToolCall toolCall : response.toolCalls()) {
                if (HANDOVER_TOOL_NAME.equals(toolCall.name())) {
                    return LoopAction.HANDOVER;
                }
                if (DELEGATE_TOOL_NAME.equals(toolCall.name())) {
                    return LoopAction.DELEGATE_WAIT;
                }
            }
            return LoopAction.CONTINUE;
        }

        return LoopAction.RESPOND;
    }

    private String handleDelegation(ToolCall toolCall, RequestContext requestContext,
                                    AgentContext parentContext, ExecContext execCtx) {
        ExecutionResult result = executeChildAgent(toolCall, requestContext, parentContext, execCtx);
        mergeChildTraceEvents(result, execCtx);
        return result.response();
    }

    private ExecutionResult handleHandover(ToolCall toolCall, RequestContext requestContext,
                                            AgentContext parentContext, ExecContext execCtx) {
        Map<String, Object> args = toolCall.arguments();
        String personaIdStr = (String) args.get("personaId");
        String delegateMessage = (String) args.get("message");

        log.info("Handover started: targetPersona={}", personaIdStr);

        PersonaId personaId = new PersonaId(personaIdStr);
        AgentBucket bucket = bucketRegistry.getBucket(parentContext.bucketId());
        var profileEntry = bucket.findProfileByPersonaId(personaId);
        AgentProfileId childProfileId = profileEntry.getKey();

        AgentContext childContext = sessionFactory.newDelegatedSession(
            requestContext, childProfileId, parentContext.sessionId());

        Instant delegationStart = Instant.now();
        try {
            ExecutionResult result = execute(requestContext, childContext.sessionId(), delegateMessage).join();
            Duration delegationLatency = Duration.between(delegationStart, Instant.now());
            execCtx.traceEvents.add(new TraceEvent.Delegation(personaIdStr, delegationLatency, true));
            mergeChildTraceEvents(result, execCtx);
            log.info("Handover completed: targetPersona={}, childSessionId={}", personaIdStr, childContext.sessionId().value());
            // Propagate the child session ID so the CLI can resume with the new agent
            return new ExecutionResult(
                    result.response(),
                    result.respondingAgent(),
                    result.trace(),
                    result.totalUsage(),
                    result.inferences() != null ? result.inferences() : List.of(),
                    childContext.sessionId()
            );
        } catch (Exception e) {
            Duration delegationLatency = Duration.between(delegationStart, Instant.now());
            execCtx.traceEvents.add(new TraceEvent.Delegation(personaIdStr, delegationLatency, false));
            log.error("Handover failed: targetPersona={}, error={}", personaIdStr, e.getMessage(), e);
            return new ExecutionResult("[Handover failed: " + e.getMessage() + "]", parentContext.agentProfileId(), null, null);
        }
    }

    private ExecutionResult executeChildAgent(ToolCall toolCall, RequestContext requestContext,
                                               AgentContext parentContext, ExecContext execCtx) {
        Map<String, Object> args = toolCall.arguments();
        String personaIdStr = (String) args.get("personaId");
        String delegateMessage = (String) args.get("message");

        log.info("Delegation started: targetPersona={}", personaIdStr);
        log.debug("Delegation message: {}", delegateMessage);

        PersonaId personaId = new PersonaId(personaIdStr);
        AgentBucket bucket = bucketRegistry.getBucket(parentContext.bucketId());
        var profileEntry = bucket.findProfileByPersonaId(personaId);
        AgentProfileId childProfileId = profileEntry.getKey();

        AgentContext childContext = sessionFactory.newDelegatedSession(
            requestContext, childProfileId, parentContext.sessionId());

        Instant delegationStart = Instant.now();
        try {
            ExecutionResult result = execute(requestContext, childContext.sessionId(), delegateMessage).join();
            Duration delegationLatency = Duration.between(delegationStart, Instant.now());
            execCtx.traceEvents.add(new TraceEvent.Delegation(personaIdStr, delegationLatency, true));
            log.info("Delegation completed: targetPersona={}", personaIdStr);
            return result;
        } catch (Exception e) {
            Duration delegationLatency = Duration.between(delegationStart, Instant.now());
            execCtx.traceEvents.add(new TraceEvent.Delegation(personaIdStr, delegationLatency, false));
            log.error("Delegation failed: targetPersona={}, error={}", personaIdStr, e.getMessage(), e);
            return new ExecutionResult("[Delegation failed: " + e.getMessage() + "]", parentContext.agentProfileId(), null, null);
        }
    }

    private String executeToolCall(ToolCall toolCall, RequestContext requestContext,
                                   AgentContext agentContext, ExecContext execCtx) {
        log.info("Executing tool: {}", toolCall.name());
        log.debug("Tool arguments: {}", toolCall.arguments());

        // Runtime guardrail: verify the tool is allowed for this agent
        if (execCtx.allowedTools != null && !execCtx.allowedTools.contains(new ToolId(toolCall.name()))) {
            log.warn("Guardrail blocked: tool '{}' not in allowed set for agent", toolCall.name());
            execCtx.traceEvents.add(new TraceEvent.Error("Guardrail blocked tool: " + toolCall.name(), "Tool not in allowed set"));
            return "[Tool not allowed: " + toolCall.name() + "]";
        }

        Tool tool = toolRegistry.get(toolCall.name());
        if (tool == null) {
            log.warn("Unknown tool requested: '{}'. Available tools: {}", toolCall.name(), toolRegistry.keySet());
            execCtx.traceEvents.add(new TraceEvent.ToolExecution(toolCall.name(), Duration.ZERO, false, "Unknown tool"));
            return "[Unknown tool: " + toolCall.name() + "]";
        }

        ToolContext toolContext = new ToolContext(
            requestContext,
            agentContext.sessionId(),
            agentContext.agentProfileId(),
            Path.of("."),
            Map.of()
        );

        Instant toolStart = Instant.now();
        try {
            Record request = objectMapper.convertValue(toolCall.arguments(), tool.requestType());
            Record response = CompletableFuture.supplyAsync(() -> tool.execute(toolContext, request))
                .orTimeout(tool.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();
            Duration toolLatency = Duration.between(toolStart, Instant.now());
            if (response == null) {
                log.error("Tool returned null response: tool={}", toolCall.name());
                execCtx.traceEvents.add(new TraceEvent.ToolExecution(toolCall.name(), toolLatency, false, "Tool returned null"));
                return "[Tool error: null response]";
            }
            String result = objectMapper.writeValueAsString(response);
            execCtx.traceEvents.add(new TraceEvent.ToolExecution(toolCall.name(), toolLatency, true, null));
            log.debug("Tool response: {}", result);
            return result;
        } catch (java.util.concurrent.CompletionException e) {
            Duration toolLatency = Duration.between(toolStart, Instant.now());
            if (e.getCause() instanceof TimeoutException) {
                String errorMsg = "Tool execution timed out after " + tool.timeout().toSeconds() + "s";
                log.error("Tool timeout: tool={}, timeout={}s", toolCall.name(), tool.timeout().toSeconds());
                execCtx.traceEvents.add(new TraceEvent.ToolExecution(toolCall.name(), toolLatency, false, errorMsg));
                return "[Tool error: " + errorMsg + "]";
            }
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("Tool execution failed: tool={}, error={}", toolCall.name(), errorMsg, e);
            execCtx.traceEvents.add(new TraceEvent.ToolExecution(toolCall.name(), toolLatency, false, errorMsg));
            return "[Tool error: " + errorMsg + "]";
        } catch (Exception e) {
            Duration toolLatency = Duration.between(toolStart, Instant.now());
            log.error("Tool execution failed: tool={}, error={}", toolCall.name(), e.getMessage(), e);
            execCtx.traceEvents.add(new TraceEvent.ToolExecution(toolCall.name(), toolLatency, false, e.getMessage()));
            return "[Tool error: " + e.getMessage() + "]";
        }
    }

    private String processToolCallsForDirectReturn(LlmResponse response, RequestContext requestContext,
                                                    AgentContext agentContext, AgentProfile profile,
                                                    ExecContext execCtx) {
        DirectReturnPolicy policy = profile.agent().directReturnPolicy();
        if (policy == null || !policy.enabled()) {
            return null;
        }

        for (ToolCall toolCall : response.toolCalls()) {
            if (policy.eligibleTools().contains(new ToolId(toolCall.name()))) {
                return executeToolCall(toolCall, requestContext, agentContext, execCtx);
            }
        }
        return null;
    }

    private void addToolResultMessages(List<LlmMessage> messages, LlmResponse response,
                                        RequestContext requestContext, AgentContext agentContext,
                                        ExecContext execCtx) {
        if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
            return;
        }

        addAssistantMessage(messages, response);
        executeToolCallsInParallel(messages, response.toolCalls(), requestContext, agentContext, execCtx);
    }

    private void executeToolCallsInParallel(List<LlmMessage> messages, List<ToolCall> toolCalls,
                                             RequestContext requestContext, AgentContext agentContext,
                                             ExecContext execCtx) {
        if (toolCalls.size() == 1) {
            // Single tool call — execute inline, no need for async overhead
            ToolCall toolCall = toolCalls.get(0);
            String output = executeSingleToolCall(toolCall, requestContext, agentContext, execCtx);
            messages.add(new LlmMessage(MessageRole.TOOL,
                List.of(new MessageContent.ToolResult(toolCall.id(), output, false))));
            return;
        }

        log.info("Executing {} tool calls in parallel", toolCalls.size());

        // Execute all tool calls in parallel
        List<CompletableFuture<Map.Entry<String, String>>> futures = toolCalls.stream()
            .map(toolCall -> CompletableFuture.supplyAsync(() ->
                Map.entry(toolCall.id(), executeSingleToolCall(toolCall, requestContext, agentContext, execCtx))
            ))
            .toList();

        // Wait for all to complete and collect results preserving order
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        // Add results in original tool call order
        for (int i = 0; i < toolCalls.size(); i++) {
            Map.Entry<String, String> result = futures.get(i).join();
            messages.add(new LlmMessage(MessageRole.TOOL,
                List.of(new MessageContent.ToolResult(result.getKey(), result.getValue(), false))));
        }
    }

    private String executeSingleToolCall(ToolCall toolCall, RequestContext requestContext,
                                          AgentContext agentContext, ExecContext execCtx) {
        if (DELEGATE_TOOL_NAME.equals(toolCall.name())) {
            log.info("Delegation to persona: {}", toolCall.arguments().get("personaId"));
            return handleDelegation(toolCall, requestContext, agentContext, execCtx);
        }
        return executeToolCall(toolCall, requestContext, agentContext, execCtx);
    }

    private void addAssistantMessage(List<LlmMessage> messages, LlmResponse response) {
        List<MessageContent> contentParts = new ArrayList<>();
        if (response.content() != null && !response.content().isEmpty()) {
            contentParts.add(new MessageContent.Text(response.content()));
        }
        if (response.toolCalls() != null) {
            for (ToolCall tc : response.toolCalls()) {
                contentParts.add(new MessageContent.ToolUse(tc.id(), tc.name(), tc.arguments()));
            }
        }
        if (!contentParts.isEmpty()) {
            messages.add(new LlmMessage(MessageRole.ASSISTANT, contentParts));
        }
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

    private void saveConversationHistory(AgentContext agentContext, List<LlmMessage> messages) {
        // Save all non-SYSTEM messages as conversation history for session continuity
        List<LlmMessage> nonSystem = messages.stream()
                .filter(m -> m.role() != MessageRole.SYSTEM)
                .toList();
        agentContext.conversationHistory().clear();
        agentContext.conversationHistory().addAll(nonSystem);
        log.debug("Saved conversation history: {} messages", nonSystem.size());
    }

    private void saveContext(AgentContext agentContext) {
        contextStore.save(agentContext);
    }

    /**
     * Collects tool instructions from all tools assigned to this agent,
     * sorted by priority (lower = earlier in prompt).
     */
    private String collectToolInstructions(AgentProfile profile) {
        var allowedTools = profile.agent().tools();
        // Build set of available tool names for condition evaluation
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
