# TroupeForge — Multi-Organization Design

> Each organization owns agent buckets. `AgentBucketId` (derived from `OrganizationId + StageContext`) is the primary isolation key.
> Request context is explicit — passed as a parameter, never ThreadLocal.
> No TenantId. No single-tenancy mode.

---

## 0. Implementation Status

The multi-org model in this document is implemented end-to-end in code. Every request carries an explicit `RequestContext`, agent sessions are isolated by `AgentSessionId`, and all bucket-scoped data goes through `AgentBucketId` (`OrganizationId + StageContext`).

The **only** major gap is persistence: both `DocumentStore` and `ContextStore` are backed by `InMemoryDocumentStore` / `InMemoryContextStore` today. Sessions, agent state, and usage events do not survive a process restart. Config trees (agent / persona / contract / model JSON) **are** read from disk via `FilesystemOrgConfigSource`. The filesystem / DynamoDB layouts described below are still the intended targets for the storage layer — only the in-memory backends exist at present.

---

## 1. Identity & Context Records (troupeforge-core)

### 1.1 Core Identity Records

```java
record OrganizationId(String value)
record UserId(String value)
record RequestId(String value)        // unique per request, strong type
record AgentSessionId(String value)   // unique per agent session in an agentic loop

// The primary isolation key — derived from OrganizationId + StageContext
// All agent grouping, storage scoping, messaging routing, and config resolution use this
record AgentBucketId(String value) {
    static AgentBucketId of(OrganizationId org, StageContext stage) {
        return new AgentBucketId(org.value() + ":" + stage.value());
    }
}
```

### 1.2 RequestContext — the first-class request header

Every request entering the system creates a `RequestContext`. This is the top-level context that flows through every layer explicitly. Never ThreadLocal.

```java
record RequestorContext(
    UserId userId,
    OrganizationId organizationId
)

record StageContext(String value) {
    // e.g., "live", "staging", "dev"
    // Determines which agent config to load for the organization
    static final StageContext LIVE = new StageContext("live");
}

record RequestContext(
    RequestId requestId,
    RequestorContext requestor,
    StageContext stage,
    Instant createdAt
) {
    OrganizationId organizationId() { return requestor.organizationId(); }
    UserId userId() { return requestor.userId(); }
    AgentBucketId bucketId() { return AgentBucketId.of(organizationId(), stage); }
}
```

**Design rationale:**
- `RequestId` enables correlation across the entire request lifecycle
- `RequestorContext` identifies who made the request and which org they belong to
- `StageContext` determines which agent config to load — the same org can have "live" vs "staging" agent farms
- `AgentBucketId` is the derived isolation key — `OrganizationId + StageContext` combined into a single typed record
- No TenantId — `AgentBucketId` is the primary key for grouping agents, scoping storage, and routing messages

### 1.3 AgentContext — per-agent session state

Each agent in an agentic loop gets its own `AgentContext`. This is NOT shared across delegation boundaries — when agent A delegates to agent B, agent B gets a fresh context.

```java
record AgentContext(
    AgentSessionId sessionId,       // identifies this agent session
    RequestId requestId,            // back-reference to the originating request
    AgentProfileId agentProfileId,  // which agent+persona is executing
    AgentBucketId bucketId,         // primary isolation key (derived from org + stage)
    Instant startedAt,              // when the session started
    Map<String, Object> state       // mutable key-value state for the agent loop
) implements Storable {
    @Override
    public String id() { return sessionId.value(); }
    @Override
    public long version() { return 0; } // managed by ContextStore
}
```

**Key rules:**
1. Each agent session has exactly one `AgentContext`
2. Context is created when an agent begins processing (new session) or loaded from storage (resumed session)
3. Context is NOT passed in the event bus — only `AgentSessionId` is passed as a reference
4. Context is stored for debugging and for continuing sessions
5. When delegating to another agent, the child gets a NEW `AgentContext` with a NEW `AgentSessionId`

### 1.4 AgentSessionId — session continuation

`AgentSessionId` enables session continuation:
- **New session:** No `AgentSessionId` passed → system generates one, creates fresh `AgentContext`
- **Resume session:** `AgentSessionId` passed → system loads existing `AgentContext` from `ContextStore`

```java
// Factory for creating new sessions
class AgentSessionFactory {
    private final ContextStore contextStore;

    AgentContext newSession(RequestContext request, AgentProfileId profileId) {
        AgentSessionId sessionId = AgentSessionId.generate();
        AgentContext ctx = new AgentContext(
            sessionId,
            request.requestId(),
            profileId,
            request.bucketId(),
            Instant.now(),
            new LinkedHashMap<>()
        );
        contextStore.save(ctx);
        return ctx;
    }

    AgentContext resumeSession(AgentSessionId sessionId) {
        return contextStore.load(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }
}
```

---

## 2. Context Propagation

### 2.1 Principle: Explicit Parameters, No Magic

Every interface method that operates on org-scoped data takes `RequestContext` or the relevant ID as a parameter. If you forget to pass it, the code does not compile. No ThreadLocal fallback.

### 2.2 Updated Core Types

**MessageEnvelope** carries `RequestContext` + `AgentSessionId` reference (NOT full `AgentContext`):

```java
record MessageEnvelope<T extends Record>(
    MessageId messageId,
    CorrelationId correlationId,
    RequestContext requestContext,       // full request header
    AgentSessionId senderSessionId,     // reference to sender's context (NOT the context itself)
    AgentAddress sender,
    AgentAddress recipient,
    ContractRef contractRef,
    MessageType type,
    T payload,
    Instant timestamp,
    Map<String, String> headers,
    int ttlSeconds
) {
    // Convenience accessors
    AgentBucketId bucketId() { return requestContext.bucketId(); }
    OrganizationId organizationId() { return requestContext.organizationId(); }
    RequestId requestId() { return requestContext.requestId(); }
}
```

**ToolContext** uses `RequestContext`:

```java
record ToolContext(
    RequestContext requestContext,
    AgentSessionId agentSessionId,
    AgentProfileId profileId,
    Path workingDirectory,
    Map<String, Object> environmentHints
)
```

**LlmRequest** uses `OrganizationId` for provider routing:

```java
record LlmRequest(
    OrganizationId organizationId,
    String model,
    List<Message> messages,
    List<ToolDefinition> tools,
    double temperature,
    int maxTokens,
    Map<String, Object> metadata
)
```

### 2.3 Full Propagation Path

```
Entry Point (test runner / programmatic API)
  → RequestContext created (RequestId, RequestorContext, StageContext)
  → RequestContext.bucketId() → AgentBucketId (derived)
  → AgentSessionFactory.newSession(requestContext, profileId)
  → MessageBus.send(envelope with RequestContext + AgentSessionId ref)
  → AgentExecutor.execute(requestContext, agentSessionId, message)
    → AgentBucketRegistry.getBucket(requestContext.bucketId()) → AgentBucket
    → ContextStore.load(agentSessionId) → AgentContext
    → PromptAssembler.assemble(requestContext, agentProfile)
    → BucketAwareLlmClient.complete(requestContext, llmRequest)
    → ToolExecutor.execute(toolContext)
    → ContextStore.save(agentContext)  // persist after each iteration
  → Response flows back via MessageBus
```

### 2.4 Interface Changes

```java
interface DocumentStore<T extends Storable> {
    StorageResult<T> get(AgentBucketId bucket, String id);
    Optional<StorageResult<T>> findById(AgentBucketId bucket, String id);
    List<StorageResult<T>> list(AgentBucketId bucket);
    List<StorageResult<T>> query(AgentBucketId bucket, QueryCriteria criteria);
    StorageResult<T> put(AgentBucketId bucket, T entity);
    boolean delete(AgentBucketId bucket, String id, long expectedVersion);
}

interface ContractRegistry {
    void register(AgentBucketId bucket, ContractDefinition contract);
    Optional<ContractDefinition> find(AgentBucketId bucket, ContractId id, ContractVersion version);
    Collection<ContractDefinition> all(AgentBucketId bucket);
}

interface AgentRegistry {
    void register(AgentBucketId bucket, AgentDescriptor descriptor);
    Optional<AgentDescriptor> findAgent(AgentBucketId bucket, AgentProfileId profileId);
    List<AgentDescriptor> findProviders(AgentBucketId bucket, ContractRef contractRef);
}
```

---

## 3. Context Storage

### 3.1 ContextStore — storage abstraction for AgentContext

`AgentContext` must be stored for two reasons:
1. **Session continuation** — pass `AgentSessionId` to resume where the agent left off
2. **Debugging** — inspect what happened during an agent session

```java
interface ContextStore {
    void save(AgentContext context);
    Optional<AgentContext> load(AgentSessionId sessionId);
    void delete(AgentSessionId sessionId);
    List<AgentContext> findByRequest(RequestId requestId);
    List<AgentContext> findByBucket(AgentBucketId bucketId, QueryCriteria criteria);
}
```

### 3.2 Filesystem Implementation (planned — not yet shipped; see Section 0)

```java
class FilesystemContextStore implements ContextStore {
    private final Path basePath;
    private final ObjectMapper objectMapper;

    // Storage layout:
    // {basePath}/{bucketId}/sessions/{agentSessionId}.json

    @Override
    public void save(AgentContext context) {
        Path file = resolvePath(context.bucketId(), context.sessionId());
        Files.createDirectories(file.getParent());
        objectMapper.writeValue(file.toFile(), context);
    }

    @Override
    public Optional<AgentContext> load(AgentSessionId sessionId) {
        // Scan bucket directories for the session file
        // In practice, we'd maintain an index or include bucketId in the lookup
    }

    private Path resolvePath(AgentBucketId bucketId, AgentSessionId sessionId) {
        return basePath.resolve(bucketId.value())
                       .resolve("sessions")
                       .resolve(sessionId.value() + ".json");
    }
}
```

### 3.3 Storage Layout

```
data/
├── acme:live/
│   └── sessions/
│       ├── sess-abc123.json       # AgentContext for session abc123
│       ├── sess-def456.json
│       └── sess-ghi789.json
├── acme:staging/
│   └── sessions/
│       └── sess-test01.json
├── globex:live/
│   └── sessions/
│       ├── sess-xyz001.json
│       └── sess-xyz002.json
```

### 3.4 Future: DynamoDB Implementation

```java
class DynamoDbContextStore implements ContextStore {
    // Table: agent_sessions
    // Partition key: bucketId
    // Sort key: agentSessionId
    // GSI: requestId → agentSessionId (for findByRequest)
    // TTL: configurable per bucket
}
```

Swap via `troupeforge.context.store=dynamodb`.

### 3.5 Context Lifecycle

```
1. Request arrives → RequestContext created
2. Agent begins processing:
   a. No AgentSessionId → AgentSessionFactory.newSession() → new AgentContext → saved
   b. AgentSessionId provided → ContextStore.load() → existing AgentContext
3. Agentic loop iteration:
   a. Agent processes message
   b. AgentContext.state updated (conversation history, intermediate results)
   c. ContextStore.save(agentContext) after each iteration
4. Delegation to child agent:
   a. NEW AgentContext created for child (new AgentSessionId)
   b. Parent's AgentSessionId NOT passed to child
   c. Parent's context NOT accessible to child
   d. Child's AgentSessionId can be stored in parent's state for tracing
5. Agent completes → final save, context remains for debugging/resumption
```

---

## 4. Agent Bucket — the Primary Agent Grouping

### 4.1 AgentBucket — per-bucket container

`AgentBucketId` (derived from `OrganizationId + StageContext`) is the primary key for clubbing agents together. Each bucket has its own fully isolated set of resolved agents, personas, contracts, model config.

```java
record AgentBucket(
    AgentBucketId bucketId,
    OrganizationId organizationId,      // back-reference
    StageContext stage,                 // back-reference
    Map<AgentId, ResolvedAgent> resolvedAgents,
    Map<AgentProfileId, AgentProfile> agentProfiles,
    ContractRegistry contractRegistry,
    AgentRegistry agentRegistry,
    ModelConfig modelConfig,
    List<PromptSection> systemPromptSections,
    Instant loadedAt,
    String configVersion        // hash for change detection
)
```

### 4.2 AgentBucketRegistry — bucket lookup

```java
interface AgentBucketRegistry {
    AgentBucket getBucket(AgentBucketId bucketId);
    void loadBucket(AgentBucketId bucketId, OrganizationId org, StageContext stage, OrgConfigSource configSource);
    void reloadBucket(AgentBucketId bucketId);
    void unloadBucket(AgentBucketId bucketId);
    Set<AgentBucketId> activeBuckets();
}
```

Implementation: `ConcurrentHashMap<AgentBucketId, AgentBucket>`. Loading is synchronized per bucket.

### 4.3 How Components Find Bucket Data

```java
class AgentExecutor {
    private final AgentBucketRegistry bucketRegistry;
    private final ContextStore contextStore;

    String execute(RequestContext request, AgentSessionId sessionId, String message) {
        // Load the agent bucket for this request
        AgentBucket bucket = bucketRegistry.getBucket(request.bucketId());

        // Load or create agent context
        AgentContext ctx = contextStore.load(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        AgentProfile profile = bucket.agentProfiles().get(ctx.agentProfileId());
        ModelConfig models = bucket.modelConfig();
        // proceed with bucket's own data
    }
}
```

No global registries. Every lookup goes through the bucket.

---

## 5. Config Loading

### 5.1 OrgConfigSource — per-org config abstraction

Each organization provides its own config. The project ships NO config (sample config only in `troupeforge-testconfig` for integration tests).

```java
interface OrgConfigSource {
    OrganizationId organizationId();
    StageContext stage();
    List<String> listAgentDirectories(String parentPath);
    String readFile(String path);
    List<String> listFiles(String directory);
    Optional<String> configVersion();   // hash/etag for change detection
}
```

### 5.2 Implementations

```java
// {basePath}/{orgId}/{stage}/agents/root/...
class FilesystemOrgConfigSource implements OrgConfigSource

// s3://{bucket}/{orgId}/{stage}/agents/root/...
class S3OrgConfigSource implements OrgConfigSource
```

### 5.3 Config Directory Layout

```
{config-root}/
├── org-acme/
│   ├── live/
│   │   ├── agents/root/...
│   │   ├── contracts/...
│   │   ├── models/models.json
│   │   └── systemprompt/...
│   └── staging/
│       ├── agents/root/...
│       ├── contracts/...
│       ├── models/models.json
│       └── systemprompt/...
├── org-globex/
│   └── live/
│       ├── agents/root/...
│       └── ...
```

### 5.4 Loading Flow

```
1. OrgLifecycleService receives OrgConfigDescriptor
2. OrgConfigSourceFactory creates OrgConfigSource
3. AgentBucketLoader reads config:
   a. Walk agent directory tree → AgentDefinitions
   b. Resolve inheritance → ResolvedAgents
   c. Load personas → compose AgentProfiles
   d. Load contracts → ContractRegistry
   e. Load models.json → ModelConfig
   f. Load system prompts → PromptSections
4. AgentBucket registered in AgentBucketRegistry
5. Bucket is live
```

### 5.5 Caching

`AgentBucket` IS the cache. Fully materialized at load time. No lazy loading. Atomic reload — swap the whole bucket reference.

---

## 6. Storage Isolation

### 6.1 Strategy: Bucket-Prefixed Collections

```java
class BucketAwareDocumentStoreFactory {
    private final DocumentStoreFactory delegate;

    <T extends Storable> DocumentStore<T> create(
            AgentBucketId bucketId, String collection, Class<T> type) {
        String bucketCollection = bucketId.value() + "/" + collection;
        return delegate.create(bucketCollection, type);
    }
}
```

### 6.2 Filesystem Layout

```
data/
├── acme:live/
│   ├── sessions/
│   └── history/
├── acme:staging/
│   ├── sessions/
│   └── history/
├── globex:live/
│   ├── sessions/
│   └── history/
```

### 6.3 Cross-Bucket Prevention

Structural — you cannot get a `DocumentStore` without `AgentBucketId`. The returned store is scoped. No "list all buckets" method exists.

---

## 7. Messaging Isolation

### 7.1 AgentBucketId on Every Envelope

`MessageEnvelope` carries `RequestContext` which derives `AgentBucketId`. The bus uses it for routing.

### 7.2 Bucket-Aware Routing

```java
class BucketAwareMessageBus implements MessageBus {
    private final MessageBus delegate;
    private final AgentBucketId bucketId;

    @Override
    <T extends Record> void send(MessageEnvelope<T> envelope) {
        if (!envelope.bucketId().equals(bucketId)) {
            throw new BucketIsolationViolation(bucketId, envelope.bucketId());
        }
        delegate.send(envelope);
    }

    @Override
    void subscribe(AgentProfileId profileId, MessageHandler handler) {
        delegate.subscribe(profileId, envelope -> {
            if (envelope.bucketId().equals(bucketId)) {
                handler.handle(envelope);
            }
        });
    }
}
```

### 7.3 Subscription Keys

```java
record BucketScopedKey(AgentBucketId bucketId, AgentProfileId profileId)
```

Agent A in `acme:live` and agent A in `acme:staging` have completely separate subscription lists.

---

## 8. LLM Isolation

### 8.1 Per-Bucket LLM Config

```java
record BucketLlmConfig(
    List<LlmProviderConfig> providers,
    RateLimitConfig rateLimits
)

record LlmProviderConfig(
    String providerName,
    boolean enabled,
    Map<String, String> credentials,
    Map<String, String> settings
)

record RateLimitConfig(
    int maxRequestsPerMinute,
    int maxTokensPerMinute,
    int maxConcurrentRequests
)
```

### 8.2 Bucket-Aware LLM Client

```java
class BucketAwareLlmClient {
    private final AgentBucketRegistry bucketRegistry;
    private final Map<String, LlmProviderFactory> providerFactories;
    private final ConcurrentHashMap<AgentBucketId, LlmClient> bucketClients;

    LlmResponse complete(RequestContext request, LlmRequest llmRequest) {
        LlmClient client = bucketClients.computeIfAbsent(
            request.bucketId(), this::buildClientForBucket);
        return client.complete(llmRequest);
    }
}
```

### 8.3 Per-Bucket Rate Limiting

```java
class BucketRateLimiter {
    private final ConcurrentHashMap<AgentBucketId, RateLimiter> requestLimiters;
    private final ConcurrentHashMap<AgentBucketId, RateLimiter> tokenLimiters;

    void acquire(AgentBucketId bucketId, int estimatedTokens) { ... }
}
```

---

## 9. Entry Point

No HTTP interfaces yet. The entry point is programmatic — used directly from tests and later from API layers.

```java
interface TroupeForgeEntryPoint {
    /**
     * Submit a request to the agent system.
     * Creates a new RequestContext and dispatches to the appropriate agent.
     */
    CompletableFuture<AgentResponse> submit(
        RequestorContext requestor,
        StageContext stage,
        AgentProfileId targetAgent,
        String message,
        @Nullable AgentSessionId resumeSessionId  // null = new session
    );
}

record AgentResponse(
    RequestId requestId,
    AgentSessionId sessionId,     // can be used to resume this session
    AgentProfileId respondingAgent,
    String response,
    Instant completedAt
)
```

### 9.1 Default Implementation

```java
class DefaultTroupeForgeEntryPoint implements TroupeForgeEntryPoint {
    private final AgentBucketRegistry bucketRegistry;
    private final AgentSessionFactory sessionFactory;
    private final MessageBus messageBus;

    @Override
    public CompletableFuture<AgentResponse> submit(
            RequestorContext requestor, StageContext stage,
            AgentProfileId targetAgent, String message,
            AgentSessionId resumeSessionId) {

        // 1. Create RequestContext
        RequestContext requestContext = new RequestContext(
            RequestId.generate(),
            requestor,
            stage,
            Instant.now()
        );

        // 2. Create or resume agent session
        AgentContext agentCtx = (resumeSessionId != null)
            ? sessionFactory.resumeSession(resumeSessionId)
            : sessionFactory.newSession(requestContext, targetAgent);

        // 3. Verify bucket exists
        AgentBucket bucket = bucketRegistry.getBucket(requestContext.bucketId());

        // 4. Build and send message envelope
        MessageEnvelope<ChatPayload> envelope = MessageEnvelope.request(
            requestContext,
            agentCtx.sessionId(),
            AgentAddress.direct(targetAgent),
            new ChatPayload(message)
        );

        // 5. Send and await response
        return messageBus.request(envelope, Duration.ofMinutes(5), ChatResponse.class)
            .thenApply(reply -> new AgentResponse(
                requestContext.requestId(),
                agentCtx.sessionId(),
                targetAgent,
                reply.payload().response(),
                Instant.now()
            ));
    }
}
```

---

## 10. Bucket Lifecycle

### 10.1 Interface

```java
interface BucketLifecycleService {
    void onboard(OrganizationId org, StageContext stage, BucketConfigDescriptor configDescriptor);
    void reload(AgentBucketId bucketId);
    void teardown(AgentBucketId bucketId);
    BucketHealth health(AgentBucketId bucketId);
}

record BucketConfigDescriptor(
    String sourceType,              // "filesystem", "s3"
    Map<String, String> properties  // source-specific: path, s3 bucket, etc.
)

record BucketHealth(
    AgentBucketId bucketId,
    OrganizationId organizationId,
    StageContext stage,
    boolean configLoaded,
    boolean llmAvailable,
    int activeAgents,
    int activeSessions,
    Instant lastActivity
)
```

### 10.2 Onboarding

```
1. BucketLifecycleService.onboard(org, stage, configDescriptor)
   → derives AgentBucketId from org + stage
2. Create OrgConfigSource from descriptor
3. Validate config (agents resolve, contracts parse, models.json valid)
4. Load AgentBucket
5. Build bucket-specific LlmClient with bucket's API keys
6. Register bucket in AgentBucketRegistry
7. Subscribe bucket's agents to MessageBus
8. Bucket is live
```

### 10.3 Hot Reload (zero downtime)

```
1. BucketLifecycleService.reload(bucketId)
   OR config watcher detects configVersion() change
2. Load new AgentBucket from config source
3. Compare configVersion — skip if unchanged
4. Atomically swap bucket (ConcurrentHashMap.put)
5. In-flight requests complete with old bucket (they hold a reference)
6. New requests use new bucket
7. Rebuild LlmClient if credentials changed
8. Old bucket GC'd when last reference released
```

---

## 11. Cost & Usage Tracking

### 11.1 Usage Event

```java
record UsageEvent(
    AgentBucketId bucketId,
    AgentProfileId agentProfileId,
    AgentSessionId sessionId,
    RequestId requestId,
    String model,
    TokenUsage tokenUsage,
    CostEstimate cost,
    Instant timestamp
)
```

### 11.2 Usage Tracker

```java
interface UsageTracker {
    void record(UsageEvent event);
    UsageSummary summary(AgentBucketId bucketId, Instant from, Instant to);
    UsageSummary summaryByAgent(AgentBucketId bucketId, AgentProfileId agentId,
                                Instant from, Instant to);
}
```

---

## 12. Module Placement

| Component | Module |
|---|---|
| `OrganizationId`, `UserId`, `RequestId`, `AgentSessionId`, `AgentBucketId` | **core** |
| `RequestContext`, `RequestorContext`, `StageContext` | **core** |
| `AgentContext`, `ContextStore` interface | **core** |
| `OrgConfigSource` interface, `UsageTracker` interface | **core** |
| `AgentBucket`, `AgentBucketRegistry`, `AgentBucketLoader` | **engine** |
| `AgentSessionFactory`, `AgentExecutor` | **engine** |
| `BucketAwareLlmClient`, `BucketRateLimiter` | **engine** |
| `FilesystemContextStore`, `FilesystemOrgConfigSource` | **infra** |
| `BucketAwareDocumentStoreFactory`, `BucketAwareMessageBus` | **infra** |
| `TroupeForgeEntryPoint`, `DefaultTroupeForgeEntryPoint` | **app** |
| `BucketLifecycleService` | **app** |
| Sample config, integration test fixtures | **testconfig** |

---

## 13. Key Design Decisions

**AgentBucketId as primary isolation key** — Derived from `OrganizationId + StageContext`. This is the single typed record used everywhere for grouping agents, scoping storage, routing messages, and resolving config. No TenantId.

**Explicit parameters over ThreadLocal** — ThreadLocal breaks with virtual threads, reactive code, and async callbacks. Explicit parameters enforce propagation at compile time.

**RequestContext as first-class header** — Every request has a `RequestContext` with `RequestorContext` (who) + `StageContext` (which config) + `RequestId` (correlation). Derives `AgentBucketId` via `bucketId()`.

**AgentContext per session, not shared** — Each agent in a delegation chain has its own context. This prevents context leakage and enables independent session resumption.

**AgentSessionId as reference, not full context** — The event bus carries only `AgentSessionId`, not the full `AgentContext`. Context is loaded from `ContextStore` when needed.

**One AgentBucket aggregate** — Single reference swap for atomic reload. No partial-reload states. Clean GC when last reference released.

**AgentBucketId on MessageEnvelope (via RequestContext)** — Single bus with bucket-aware routing. Simpler monitoring and dead letter handling.

**No single-tenancy mode** — Every request has an organization and stage. Simplifies the codebase — one path, not two.

**No HTTP interfaces yet** — Entry point is programmatic (`TroupeForgeEntryPoint`). HTTP/WebSocket layers will be added later on top of this.

**Context stored for debugging and continuation** — `ContextStore` persists `AgentContext` after every iteration, scoped by `AgentBucketId`. File-based initially, abstracted for DynamoDB later.
