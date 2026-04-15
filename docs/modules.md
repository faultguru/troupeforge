# Modules

TroupeForge is a Gradle multi-module project. Modules are split by **deployment boundary and substitutability**, not by arbitrary layers.

```
              troupeforge-core
             /       |        \
            /        |         \
  troupeforge-engine  troupeforge-infra
        |
  troupeforge-tools
        \        |        /
         \       |       /
          troupeforge-app ──▶ REST ──▶ troupeforge-client
                  ▲
                  │ (test-only)
          troupeforge-testconfig
          troupeforge-tests
          troupeforge-integtests
```

## troupeforge-core

**Purpose:** Pure domain records, interfaces, SPI. Zero Spring, zero Guava. Only Jackson annotations for JSON.

**Key packages:**

| Package | Contents |
|---|---|
| `id` | Typed identity records: `AgentId`, `PersonaId`, `AgentProfileId`, `AgentSessionId`, `UserId`, `OrganizationId`, `RequestId`, `TierId`, `CapabilityId`, `GuardrailId`, `ToolId`, `ContractCapabilityId` |
| `agent` | `AgentDefinition`, `ResolvedAgent`, `AgentType`, `InheritableSet`, `InheritanceAction`, `PromptSection`, `DirectReturnPolicy` |
| `persona` | `PersonaDefinition`, `PersonaStyle`, `Verbosity` |
| `context` | `RequestContext`, `AgentContext`, `RequestorContext`, `StageContext` |
| `contract` | `ContractDefinition`, `ContractId`, `ContractVersion`, `ContractRef` |
| `message` | `MessageEnvelope`, `MessageBus`, `MessageHandler`, `ContractHandler`, `AgentAddress`, `MessageType`, `ErrorPayload` |
| `llm` | `LlmProvider`, `LlmRequest`, `LlmResponse`, `Message`, `MessageRole`, `MessageContent`, `ToolCall`, `ToolDefinition`, `TokenUsage`, `LlmStreamEvent` |
| `model` | `TierId`, `ModelTierDefinition`, `ModelConfig`, `ModelSelection` |
| `storage` | `Storable`, `DocumentStore`, `ContextStore`, `StorageResult`, `QueryCriteria` |
| `tool` | `Tool`, `ToolResult`, `ToolContext`, `ToolBinding`, `AgentToolSet`, `ToolParam` |
| `registry` | `ContractRegistry`, `AgentRegistry` |
| `bucket` | `AgentBucketId`, bucket-aware interfaces |
| `entrypoint` | `TroupeForgeEntryPoint`, `AgentResponse` |

## troupeforge-engine

**Purpose:** Agent execution, configuration loading, prompt assembly, model selection, session management. Depends on `-core`.

**Key packages:**

| Package | Contents |
|---|---|
| `bucket` | `AgentBucket`, `AgentBucketLoader`, `AgentBucketRegistry` |
| `config` | `AgentConfigLoader`, `AgentInheritanceResolver`, `PersonaComposer`, `PersonaConfigLoader`, `ContractConfigLoader`, `ModelConfigLoader`, `ProviderConfigLoader`, `SystemPromptLoader` |
| `execution` | `AgentExecutor`, `AgentExecutorImpl` (max loop iterations = 10), `ExecutionResult`, `ExecutionTrace`, `TraceEvent`, `InferenceSummary`, `CostAccumulator` |
| `stream` | `StreamingAgentExecutor` — Flux-based variant for SSE |
| `model` | `ModelSelectionService`, `ModelResolver`, `ComplexityAnalyzer` / `ComplexityAnalyzerImpl` |
| `prompt` | `PromptAssembler` — orders `PromptSection`s by `order` |
| `session` | `AgentSessionFactory` — new / resume session creation |
| `tool` | `ToolSchemaGenerator` — derives JSON schemas from record + `@ToolParam` annotations |

## troupeforge-infra

**Purpose:** Concrete backends. Depends on `-core`.

**Key types:**

- `ClaudeLlmProvider` / `ClaudeLlmProviderFactory` — Anthropic HTTP client with retry, backoff, streaming, OAuth + API key auth
- `InMemoryDocumentStore` — `ConcurrentHashMap` per bucket, optimistic locking via version compare
- `InMemoryContextStore` — bucketed agent session storage with secondary index by `RequestId`
- `InMemoryMessageBus` — contract / direct / broadcast routing, request/reply via `CompletableFuture` correlation
- `FilesystemOrgConfigSource` — reads agent / persona / contract / model JSON trees from disk

> No filesystem / DB document or context stores ship yet. See the top of `.troupeforge/DESIGN.md` for the current status.

## troupeforge-tools

**Purpose:** Built-in `Tool` implementations. Depends on `-core` and `-engine`.

| Package | Tools |
|---|---|
| `delegation` | `DelegateToAgentTool`, `HandoverToAgentTool`, `ListAgentsTool` |
| `file` | `ReadFileTool`, `WriteFileTool`, `ListFilesTool`, `HeadFileTool`, `SearchFilesTool` |
| `system` | `ShellCommandTool` |
| `reasoning` | `ThinkTool` |
| `memory` | `MemoryTool` |
| `util` | `CalculatorTool` |
| `web` | `WebFetchTool` |

Each tool is a plain Java class registered via a `ToolRegistry` bean; input schemas are generated from a Java record request type annotated with `@ToolParam`.

## troupeforge-app

**Purpose:** Spring Boot application, REST API, lifecycle wiring. Depends on every other production module.

**Key types:**

- `TroupeForgeApplication` — Spring Boot main class
- `TroupeForgeLocalRunner` — programmatic local runner for testing without HTTP
- `TroupeForgeEntryPointImpl` — bridges `RequestContext` + `AgentSessionFactory` + `AgentExecutor`
- `TroupeForgeConfig`, `BucketAutoLoadConfig` — Spring `@Configuration`
- `BucketLifecycleServiceImpl` — onboard / reload / teardown of bucketed agent farms
- REST layer (`rest` package): `ChatController`, `StreamChatController`, `AgentController`, `SessionController`, `StatusController`, `GlobalExceptionHandler`
- `RequestCorrelationFilter` — populates SLF4J MDC with request / session / bucket ids

## troupeforge-client

A small CLI REPL that talks to the REST API. Not used by the engine. Provides:

- `TroupeForgeClient` — entry point
- `ApiClient` — HTTP client over `/api/chat` and `/api/chat/stream`
- `CommandProcessor`, `OutputFormatter`, `SessionPersistence`

Launch scripts: `troupeforge-chat.cmd` (cmd), `troupeforge-chat.ps1` (PowerShell).

## troupeforge-testconfig

**Not loaded in production.** Contains a sample config tree used by `BucketAutoLoadConfig` during local development and by the integration tests:

- `config/agents/` — `root-agent.json` + child agent folders (architect, calculator, dispatcher, echo, greeter, mock-agent, researcher). Each child has its own `personas/` subfolder.
- `config/contracts/` — `chat-contract.json`, `architecture-review-contract.json`, `research-contract.json`, `web-search-contract.json`
- `config/models/models.json` — global tier definitions
- `config/models/providers/` — LLM provider configuration (`claude-oauth-provider.json`)

## troupeforge-tests and troupeforge-integtests

Test-only modules. `-tests` holds unit and engine-level tests that do not need the Spring context; `-integtests` spins up the full app with `troupeforge-testconfig` on the classpath.
