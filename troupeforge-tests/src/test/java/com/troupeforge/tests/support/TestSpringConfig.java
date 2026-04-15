package com.troupeforge.tests.support;

import com.troupeforge.app.entrypoint.TroupeForgeEntryPointImpl;
import com.troupeforge.core.entrypoint.TroupeForgeEntryPoint;
import com.troupeforge.core.id.TierId;
import com.troupeforge.core.llm.LlmProvider;
import com.troupeforge.core.model.ModelConfig;
import com.troupeforge.core.storage.ContextStore;
import com.troupeforge.core.tool.Tool;
import com.troupeforge.engine.bucket.AgentBucketLoader;
import com.troupeforge.engine.bucket.AgentBucketLoaderImpl;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.engine.bucket.AgentBucketRegistryImpl;
import com.troupeforge.engine.config.AgentConfigLoader;
import com.troupeforge.engine.config.AgentConfigLoaderJsonImpl;
import com.troupeforge.engine.config.AgentInheritanceResolver;
import com.troupeforge.engine.config.AgentInheritanceResolverImpl;
import com.troupeforge.engine.config.ContractConfigLoader;
import com.troupeforge.engine.config.ContractConfigLoaderJsonImpl;
import com.troupeforge.engine.config.ModelConfigLoader;
import com.troupeforge.engine.config.ModelConfigLoaderJsonImpl;
import com.troupeforge.engine.config.PersonaComposer;
import com.troupeforge.engine.config.PersonaComposerImpl;
import com.troupeforge.engine.config.PersonaConfigLoader;
import com.troupeforge.engine.execution.AgentExecutor;
import com.troupeforge.engine.execution.AgentExecutorImpl;
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
import com.troupeforge.infra.storage.InMemoryContextStore;
import com.troupeforge.tools.delegation.DelegateToAgentTool;
import com.troupeforge.tools.delegation.HandoverToAgentTool;
import com.troupeforge.tools.delegation.ListAgentsTool;
import com.troupeforge.tools.file.HeadFileTool;
import com.troupeforge.tools.file.ListFilesTool;
import com.troupeforge.tools.file.ReadFileTool;
import com.troupeforge.tools.file.SearchFilesTool;
import com.troupeforge.tools.file.WriteFileTool;
import com.troupeforge.tools.system.ShellCommandTool;
import com.troupeforge.tools.web.WebFetchTool;
import com.troupeforge.tools.reasoning.ThinkTool;
import com.troupeforge.tools.memory.MemoryTool;
import com.troupeforge.tools.util.CalculatorTool;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class TestSpringConfig {

    @Bean
    public MockLlmProvider mockLlmProvider() {
        return new MockLlmProvider();
    }

    @Bean
    @Primary
    public LlmProvider llmProvider(MockLlmProvider mock) {
        return mock;
    }

    @Bean
    public ContextStore contextStore() {
        return new InMemoryContextStore();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

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

    @Bean
    public PromptAssembler promptAssembler() {
        return new PromptAssemblerImpl();
    }

    @Bean
    public ModelConfig fallbackModelConfig() {
        Map<TierId, com.troupeforge.core.model.ModelTierDefinition> tiers = new LinkedHashMap<>();
        tiers.put(new TierId("TRIVIAL"),
                new com.troupeforge.core.model.ModelTierDefinition(
                        new TierId("TRIVIAL"), "mock-model", 2048, 0.3, "Trivial tier"));
        tiers.put(new TierId("SIMPLE"),
                new com.troupeforge.core.model.ModelTierDefinition(
                        new TierId("SIMPLE"), "mock-model", 4096, 0.7, "Simple tier"));
        tiers.put(new TierId("STANDARD"),
                new com.troupeforge.core.model.ModelTierDefinition(
                        new TierId("STANDARD"), "mock-model", 4096, 0.7, "Standard tier"));
        tiers.put(new TierId("ADVANCED"),
                new com.troupeforge.core.model.ModelTierDefinition(
                        new TierId("ADVANCED"), "mock-model", 8192, 0.5, "Advanced tier"));
        tiers.put(new TierId("COMPLEX"),
                new com.troupeforge.core.model.ModelTierDefinition(
                        new TierId("COMPLEX"), "mock-model", 8192, 0.3, "Complex tier"));
        tiers.put(new TierId("EXPERT"),
                new com.troupeforge.core.model.ModelTierDefinition(
                        new TierId("EXPERT"), "mock-model", 16384, 0.3, "Expert tier"));
        return new ModelConfig(Map.of(), tiers, "mock-model", 4096);
    }

    @Bean
    public ComplexityAnalyzer complexityAnalyzer(ModelConfig fallbackModelConfig) {
        List<TierId> orderedTiers = new ArrayList<>(fallbackModelConfig.tiers().keySet());
        return new ComplexityAnalyzerImpl(orderedTiers);
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

    @Bean
    public AgentBucketLoader bucketLoader(AgentConfigLoaderJsonImpl jsonConfigLoader,
                                          ContractConfigLoader contractConfigLoader,
                                          AgentInheritanceResolver inheritanceResolver,
                                          PersonaComposer personaComposer,
                                          ModelConfigLoader modelConfigLoader) {
        return new AgentBucketLoaderImpl(
                jsonConfigLoader,
                jsonConfigLoader,
                contractConfigLoader,
                inheritanceResolver,
                personaComposer,
                modelConfigLoader
        );
    }

    @Bean
    public AgentBucketRegistry bucketRegistry(AgentBucketLoader bucketLoader) {
        return new AgentBucketRegistryImpl(bucketLoader);
    }

    @Bean
    public AgentSessionFactory sessionFactory(ContextStore contextStore) {
        return new AgentSessionFactoryImpl(contextStore);
    }

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
                bucketRegistry,
                sessionFactory,
                promptAssembler,
                modelSelectionService,
                llmProvider,
                contextStore,
                toolRegistry,
                objectMapper
        );
    }

    @Bean
    public TroupeForgeEntryPoint entryPoint(AgentExecutor agentExecutor,
                                             AgentSessionFactory sessionFactory) {
        return new TroupeForgeEntryPointImpl(agentExecutor, sessionFactory);
    }
}
