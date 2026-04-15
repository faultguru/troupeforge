package com.troupeforge.app.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.llm.LlmProvider;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.model.ModelTierDefinition;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.tool.Tool;
import com.troupeforge.engine.bucket.AgentBucketLoader;
import com.troupeforge.engine.bucket.AgentBucketLoaderImpl;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.bucket.AgentBucketRegistryImpl;
import com.troupeforge.engine.config.AgentConfigLoaderJsonImpl;
import com.troupeforge.engine.config.AgentInheritanceResolver;
import com.troupeforge.engine.config.AgentInheritanceResolverImpl;
import com.troupeforge.engine.config.ContractConfigLoader;
import com.troupeforge.engine.config.ContractConfigLoaderJsonImpl;
import com.troupeforge.engine.config.ModelConfigLoader;
import com.troupeforge.engine.config.ModelConfigLoaderJsonImpl;
import com.troupeforge.engine.config.PersonaComposer;
import com.troupeforge.engine.config.PersonaComposerImpl;
import com.troupeforge.engine.execution.AgentExecutor;
import com.troupeforge.engine.execution.AgentExecutorImpl;
import com.troupeforge.engine.stream.StreamingAgentExecutor;
import com.troupeforge.engine.model.ComplexityAnalyzer;
import com.troupeforge.engine.model.ComplexityAnalyzerImpl;
import com.troupeforge.engine.model.ModelResolver;
import com.troupeforge.engine.model.ModelResolverImpl;
import com.troupeforge.engine.model.ModelSelectionService;
import com.troupeforge.engine.model.ModelSelectionServiceImpl;
import com.troupeforge.engine.prompt.PromptAssembler;
import com.troupeforge.engine.prompt.PromptAssemblerImpl;
import com.troupeforge.engine.session.AgentSessionFactory;
import com.troupeforge.engine.session.AgentSessionFactoryImpl;
import com.troupeforge.infra.llm.ClaudeLlmProvider;
import com.troupeforge.infra.llm.ClaudeProviderConfig;
import com.troupeforge.infra.storage.InMemoryContextStore;
import com.troupeforge.tools.delegation.DelegateToAgentTool;
import com.troupeforge.tools.delegation.HandoverToAgentTool;
import com.troupeforge.tools.delegation.ListAgentsTool;
import com.troupeforge.tools.file.HeadFileTool;
import com.troupeforge.tools.file.ListFilesTool;
import com.troupeforge.tools.file.ReadFileTool;
import com.troupeforge.tools.file.SearchFilesTool;
import com.troupeforge.tools.file.WriteFileTool;
import com.troupeforge.tools.memory.MemoryTool;
import com.troupeforge.tools.reasoning.ThinkTool;
import com.troupeforge.tools.system.ShellCommandTool;
import com.troupeforge.tools.util.CalculatorTool;
import com.troupeforge.tools.web.WebFetchTool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class TroupeForgeConfig {

    // ---- Jackson ----

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    // ---- LLM provider ----

    @Bean
    public LlmProvider llmProvider(ObjectMapper objectMapper,
                                    @Value("${troupeforge.llm.base-url:https://api.anthropic.com}") String baseUrl,
                                    @Value("${troupeforge.llm.credential-paths:#{null}}") List<String> credentialPaths) {
        List<String> paths = credentialPaths != null ? credentialPaths
                : List.of(System.getProperty("user.home") + "/.claude/.credentials.json");
        return new ClaudeLlmProvider(objectMapper, new ClaudeProviderConfig(baseUrl, paths));
    }

    // ---- Storage ----

    @Bean
    public ContextStore contextStore() {
        return new InMemoryContextStore();
    }

    // ---- Config loaders ----

    @Bean
    public AgentConfigLoaderJsonImpl jsonConfigLoader(ObjectMapper objectMapper) {
        return new AgentConfigLoaderJsonImpl(objectMapper);
    }

    @Bean
    public ContractConfigLoader contractConfigLoader(ObjectMapper objectMapper) {
        return new ContractConfigLoaderJsonImpl(objectMapper);
    }

    @Bean
    public ModelConfigLoader modelConfigLoader(ObjectMapper objectMapper) {
        return new ModelConfigLoaderJsonImpl(objectMapper);
    }

    // ---- Inheritance & composition ----

    @Bean
    public AgentInheritanceResolver inheritanceResolver() {
        return new AgentInheritanceResolverImpl();
    }

    @Bean
    public PersonaComposer personaComposer() {
        return new PersonaComposerImpl(
                agentId -> Collections.emptyList(),
                agentId -> Collections.emptyList()
        );
    }

    // ---- Prompt assembly ----

    @Bean
    public PromptAssembler promptAssembler() {
        return new PromptAssemblerImpl();
    }

    // ---- Model selection ----

    @Bean
    public ModelConfig fallbackModelConfig() {
        Map<TierId, ModelTierDefinition> tiers = new LinkedHashMap<>();
        tiers.put(new TierId("TRIVIAL"),
                new ModelTierDefinition(new TierId("TRIVIAL"), "claude-haiku-4-5-20251001", 2048, 0.3, "Trivial tier"));
        tiers.put(new TierId("SIMPLE"),
                new ModelTierDefinition(new TierId("SIMPLE"), "claude-haiku-4-5-20251001", 4096, 0.7, "Simple tier"));
        tiers.put(new TierId("STANDARD"),
                new ModelTierDefinition(new TierId("STANDARD"), "claude-sonnet-4-6-20260327", 4096, 0.7, "Standard tier"));
        tiers.put(new TierId("ADVANCED"),
                new ModelTierDefinition(new TierId("ADVANCED"), "claude-sonnet-4-6-20260327", 8192, 0.5, "Advanced tier"));
        tiers.put(new TierId("COMPLEX"),
                new ModelTierDefinition(new TierId("COMPLEX"), "claude-sonnet-4-6-20260327", 8192, 0.3, "Complex tier"));
        tiers.put(new TierId("EXPERT"),
                new ModelTierDefinition(new TierId("EXPERT"), "claude-sonnet-4-6-20260327", 16384, 0.3, "Expert tier"));
        return new ModelConfig(Map.of(), tiers, "claude-haiku-4-5-20251001", 4096);
    }

    @Bean
    public ComplexityAnalyzer complexityAnalyzer(ModelConfig fallbackModelConfig) {
        return new ComplexityAnalyzerImpl(new ArrayList<>(fallbackModelConfig.tiers().keySet()));
    }

    @Bean
    public ModelResolver modelResolver() {
        return new ModelResolverImpl();
    }

    @Bean
    public ModelSelectionService modelSelectionService(ModelResolver modelResolver,
                                                       ComplexityAnalyzer complexityAnalyzer,
                                                       ModelConfig fallbackModelConfig) {
        return new ModelSelectionServiceImpl(modelResolver, complexityAnalyzer, fallbackModelConfig);
    }

    // ---- Bucket loading ----

    @Bean
    public AgentBucketLoader bucketLoader(AgentConfigLoaderJsonImpl jsonConfigLoader,
                                          ContractConfigLoader contractConfigLoader,
                                          AgentInheritanceResolver inheritanceResolver,
                                          PersonaComposer personaComposer,
                                          ModelConfigLoader modelConfigLoader) {
        return new AgentBucketLoaderImpl(
                jsonConfigLoader, jsonConfigLoader,
                contractConfigLoader, inheritanceResolver,
                personaComposer, modelConfigLoader
        );
    }

    @Bean
    public AgentBucketRegistry bucketRegistry(AgentBucketLoader bucketLoader) {
        return new AgentBucketRegistryImpl(bucketLoader);
    }

    // ---- Session management ----

    @Bean
    public AgentSessionFactory sessionFactory(ContextStore contextStore) {
        return new AgentSessionFactoryImpl(contextStore);
    }

    // ---- Tools ----

    @Bean
    public DelegateToAgentTool delegateToAgentTool() {
        return new DelegateToAgentTool();
    }

    @Bean
    public HandoverToAgentTool handoverToAgentTool() {
        return new HandoverToAgentTool();
    }

    @Bean
    public ListAgentsTool listAgentsTool(AgentBucketRegistry bucketRegistry) {
        return new ListAgentsTool(bucketRegistry);
    }

    @Bean
    public HeadFileTool headFileTool() {
        return new HeadFileTool();
    }

    @Bean
    public ListFilesTool listFilesTool() {
        return new ListFilesTool();
    }

    @Bean
    public ReadFileTool readFileTool() {
        return new ReadFileTool();
    }

    @Bean
    public SearchFilesTool searchFilesTool() {
        return new SearchFilesTool();
    }

    @Bean
    public WriteFileTool writeFileTool() {
        return new WriteFileTool();
    }

    @Bean
    public ShellCommandTool shellCommandTool() {
        return new ShellCommandTool();
    }

    @Bean
    public WebFetchTool webFetchTool() {
        return new WebFetchTool();
    }

    @Bean
    public ThinkTool thinkTool() {
        return new ThinkTool();
    }

    @Bean
    public MemoryTool memoryTool() {
        return new MemoryTool();
    }

    @Bean
    public CalculatorTool calculatorTool() {
        return new CalculatorTool();
    }

    @Bean
    public Map<String, Tool> toolRegistry(List<Tool> tools) {
        return tools.stream().collect(Collectors.toMap(Tool::name, Function.identity()));
    }

    // ---- Execution ----

    @Bean
    public AgentExecutor agentExecutor(AgentBucketRegistry bucketRegistry,
                                       AgentSessionFactory sessionFactory,
                                       PromptAssembler promptAssembler,
                                       ModelSelectionService modelSelectionService,
                                       LlmProvider llmProvider,
                                       ContextStore contextStore,
                                       @Qualifier("toolRegistry") Map<String, Tool> toolRegistry,
                                       ObjectMapper objectMapper) {
        return new AgentExecutorImpl(
                bucketRegistry, sessionFactory, promptAssembler,
                modelSelectionService, llmProvider, contextStore,
                toolRegistry, objectMapper
        );
    }

    @Bean
    public StreamingAgentExecutor streamingAgentExecutor(AgentBucketRegistry bucketRegistry,
                                                          AgentSessionFactory sessionFactory,
                                                          PromptAssembler promptAssembler,
                                                          ModelSelectionService modelSelectionService,
                                                          LlmProvider llmProvider,
                                                          ContextStore contextStore,
                                                          @Qualifier("toolRegistry") Map<String, Tool> toolRegistry) {
        return new StreamingAgentExecutor(
                bucketRegistry, sessionFactory, promptAssembler,
                modelSelectionService, llmProvider, contextStore,
                toolRegistry
        );
    }
}
