# LizzyCode Agent Orchestration & Configuration Reference

> Extracted from `D:/work/workspaces/lizzycode/server/`
> Package root: `dev.lizzycode`

---

## Architecture Overview

LizzyCode is a **task-driven multi-agent orchestration system** built in Java with Guice DI. Three main layers:

1. **Agent Configuration** — Role/persona definitions, model dispatch, prompt assembly
2. **Task Orchestration** — Issue lifecycle, scheduling, processor pipelines
3. **Agent Engine** — LLM execution loop, tool calling, session tracking

---

## 1. Agent Configuration

### 1.1 Configuration Hierarchy

```
Role (RoleDefinition)
  └── Persona (PersonaDefinition)  ← overrides role-level settings
       └── AgentProfileDefinition  ← fully resolved (role + persona bundled)
```

**Override rules:**
| Field | Persona behavior |
|---|---|
| `identity` | **Replaces** role identity |
| `rules` | **Appends** to role rules |
| `strategy` | **Replaces** role strategy |
| `tools` | **Replaces** role tools (empty = no tools) |
| `capabilities` | **Replaces** role capabilities (no merge) |
| `guardrails` | **Replaces** role guardrails (no merge) |
| `modelBucket` | **Replaces** role model bucket |
| `style` | Persona-only (tone, verbosity, emoji) |

### 1.2 Role Definition

**File:** `domain/.../role/RoleDefinition.java`
**Storage:** `config/agents/{roleId}/{roleId}.json`

```java
@Value @Builder
class RoleDefinition {
    String id;
    String name;
    RoleType type;          // WORKER, ROBOT, HANDLER, REVIEWER, HUMAN
    String description;
    RolePrompt prompt;      // identity, rules, strategy, tools
    String modelBucket;     // e.g., "CODE_WRITE", "CODE_REVIEW"
    List<String> personaIds;
    List<String> capabilities;  // e.g., "coding", "code-review", "planning"
    List<String> guardrails;    // e.g., "no-code-changes"
    int maxConcurrency;         // default: 1
}
```

**RoleType enum:**
- `WORKER` — User-facing agents with multiple personas
- `ROBOT` — Background automation, synthetic "robot" persona auto-created
- `HANDLER` — Internal routing/dispatch
- `REVIEWER` — Automated verification
- `HUMAN` — Human participants (scheduler skips these)

**RolePrompt:**
```java
@Value class RolePrompt {
    String identity;
    List<String> rules;
    String strategy;
    List<String> tools;
}
```

### 1.3 Persona Definition

**File:** `domain/.../persona/PersonaDefinition.java`
**Storage:** `config/agents/{roleId}/{personaId}.json`

```java
@Value @Builder
class PersonaDefinition {
    String id;
    String roleId;
    String name, displayName, avatar, description;
    PromptOverrides overrides;
    List<String> capabilities;   // replaces role's list
    List<String> guardrails;     // replaces role's list
    String modelBucket;          // replaces role's bucket
    boolean disabled;            // excluded from runtime if true
}
```

**PromptOverrides:**
```java
@Value class PromptOverrides {
    String identity;
    List<String> rules;
    String strategy;
    List<String> tools;
    PersonaStyle style;
}
```

**PersonaStyle:**
```java
@Value class PersonaStyle {
    String tone;       // e.g., "humble, careful, honest"
    String verbosity;  // "concise" | "detailed" | "balanced"
    String emoji;      // e.g., "🔧"
}
```

### 1.4 Agent Profile ID

**File:** `domain/.../id/AgentProfileId.java`

Composite identifier: `roleId:personaId` (e.g., `"coder:nate"`)

```java
class AgentProfileId {
    RoleId roleId;
    PersonaId personaId;

    // Constants
    static SYSTEM = ("system", "system")
    static SYSTEM_STARTUP_RECOVERY = ("system", "startup-recovery")
    static HUMAN_ADMIN = ("human", "admin")

    // Parsing
    static AgentProfileId of(String "role:persona")
    String getId()          // "roleId:personaId"
    String getDisplayName() // "PersonaId [RoleId]"
}
```

### 1.5 Configuration Loading

**File:** `agentconfig/.../config/AgentConfigLoader.java` (Singleton)

**Load process:**
1. Iterates `ConfigSource.listAgentIds()` (folder names under `config/agents/`)
2. For each agent folder:
   - Loads `{roleId}.json` as `RoleDefinition`
   - If ROBOT: creates synthetic `PersonaId.ROBOT` persona
   - Otherwise: loads all other `.json` files as `PersonaDefinition`
   - Skips disabled personas
3. Stores into `Map<RoleId, RoleDefinition>` and `Map<AgentProfileId, PersonaDefinition>`

**Key methods:**
```java
AgentProfileDefinition resolve(AgentProfileId)
Optional<RoleDefinition> getRoleDefinition(RoleId)
List<AgentProfileDefinition> getPersonasForAgent(RoleId)
boolean isAgentEnabled(String agentProfileString)
```

**ConfigSource interface** — abstracts filesystem/S3/classpath:
```java
interface ConfigSource {
    List<String> listAgentIds();
    String readFile(String roleId, String filename);
    List<String> listAgentFiles(String roleId);
    String readSystemPrompt(String relativePath);
    String readModelsConfig();
    String readServiceProviders();
}
```

### 1.6 Agent Profile Resolver

**File:** `agentconfig/.../config/AgentProfileResolver.java`

Resolves multiple input formats to `AgentProfileId`:
- `"role:persona"` — canonical
- `"role/persona"` — slash variant
- `"role"` — auto-resolves if single persona
- persona name lookup
- `"PersonaName [RoleName]"` — backward compat

---

## 2. Model Dispatch & Complexity

### 2.1 Model Configuration

**File:** `config/models/models.json`

```java
@Value class ModelConfig {
    Map<String, String> aliases;        // "haiku" → full model ID
    Map<String, ModelBucket> buckets;   // "CODE_WRITE" → tiers
    ModelDefaults defaults;             // fallbackTier: "standard", fallbackModel: "sonnet"
}
```

**Buckets** (known): `CODE_REVIEW`, `CODE_WRITE`, `PLANNING`, `ARCHITECT`, `DISPATCH`, `TRIAGE`, `GREETING`, `ROBOT`, `COORDINATION`, `DESIGNER`, `VIBE_DESIGNER`, `TECH_WRITE`, `NONE`

Each bucket has tiers:
```java
@Value class ModelBucket {
    String description;
    Map<String, ModelTier> tiers;  // "simple", "standard", "complex"
}

@Value class ModelTier {
    String model;        // alias or full ID
    String description;
    int maxTokens;       // default: 8192
}
```

### 2.2 Complexity Analysis

**File:** `agentconfig/.../dispatch/DefaultComplexityAnalyzer.java`

```
COMPLEX:  linesChanged > 500 OR filesChanged > 15
SIMPLE:   linesChanged < 50 AND filesChanged <= 3
STANDARD: everything else
```

Input: `IssueContext` (issueDescription, linesChanged, filesChanged, fileTypes, metadata)

### 2.3 Model Dispatcher

**File:** `agentconfig/.../dispatch/ModelDispatcher.java`

```java
ModelSelection select(String modelBucket, TaskComplexity complexity)
// 1. Lookup bucket → 2. Map complexity to tier → 3. Resolve alias → 4. Return ModelSelection
// Fallback: sonnet, 8192 tokens
```

---

## 3. System Prompts

### 3.1 Prompt Structure

**Directory:** `config/systemprompt/`
```
systemprompt/
├── core/           — Always included for every agent
├── capabilities/   — Per-agent based on capabilities list
├── guardrails/     — Per-agent based on guardrails list
└── asks/           — Reserved
```

**Loader:** `agentconfig/.../config/SystemPromptLoader.java`

### 3.2 Prompt Assembly

**File:** `agentengine/.../prompt/PromptAssembler.java`

**Section order:**
1. `SYSTEM_IDENTITY` — Agent name + description/identity + merged rules
2. `CORE_RULES` — Always included
3. `CAPABILITIES` — From capabilities list (persona overrides role)
4. `GUARDRAILS` — From guardrails list (persona overrides role)
5. `STRATEGY_PROMPT` — Custom strategy
6. `PERSONA_STYLE` — Tone, verbosity, emoji
7. `AVAILABLE_TOOLS` — Tool descriptions
8. `CONTEXT_MEMORY` — History
9. `TASK` — Issue briefing
10. `RESPONSE_CONTRACT` — Optional structured format

**Capability parameterization:** `"agentlist[coder]"` → name=`agentlist`, arg=`coder`, template resolves `${arg}`

---

## 4. Task Orchestration

### 4.1 Issue Model

**File:** `taskorchestrator/.../issues/type/Issue.java`

```java
class Issue {
    String id;                    // format: ISSUE-XXXX
    String title, description;
    IssueStatus status;
    IssueType type;               // EPIC, STORY, TASK
    AgentProfileId assignee;
    String result;
    int priority;                 // 0-10
    String parentId;
    String collabDocSlug;
    List<String> subtaskIds;
    List<String> tags;
    List<IssueComment> comments;
    List<IssueHistoryEntry> history;
    IssueMetadata metadata;
    List<String> dependencies;
}
```

### 4.2 Issue Status Lifecycle

```
BACKLOG → PENDING → IN_PROGRESS → NEEDS_REVIEW → IN_REVIEW → CLOSED
```

**Issue hierarchy:** `EPIC → STORY → TASK`

### 4.3 Workflow Tags

**File:** `taskorchestrator/.../executor/IssueTag.java`

Tag pairs (trigger → completion):
- `#needsdesign` → `#designdone`
- `#needstaskbreakdown` → `#taskscreated`
- `#needsresearch` → `#researchcomplete`
- `#needspr` → `#prready` → `#prapproved`
- `#humanreview`, `#verified`, `#retried`
- `#unresolved comments` (preserveStatus processor)

### 4.4 Issue Scheduler

**File:** `taskorchestrator/.../scheduler/IssueScheduler.java`

**Dispatch cycle (daemon thread):**
1. **Phase 1:** Process tagged issues (processor-eligible)
2. **Phase 2:** Dispatch PENDING TASKs to worker pool
3. **Phase 3:** Auto-assign unassigned issues

**Configuration (`IssueSchedulerConfig`):**
```
MAX_IN_PROGRESS_TASKS = 10
MAX_ACTIVE_SESSIONS = 15
DISPATCH_DELAY_MS = 5_000
MIN_TASK_AGE_MS = 5_000
```

Wakes on issue changes via `IssueChangeListener`. Respects LLM backoff state.

### 4.5 Processor System

**File:** `taskorchestrator/.../scheduler/processor/IssueProcessorDispatcher.java`

Processors handle specialized workflows separate from normal task execution.

**Processor entry:**
```java
class ProcessorEntry {
    IssueTag tag;              // trigger tag
    String provider;           // agent profile, CURRENT_ASSIGNEE, or SYSTEM_LIFECYCLE
    ProcessorLambda processor;
    String description;
    boolean preserveStatus;    // don't move to IN_PROGRESS
    boolean isGuestProvider;   // don't change assignee
    boolean keepAssignee;      // preserve assignee after completion
    String fallbackProvider;
}
```

**Built-in processors:**
- `CommentTagProcessor` — Reply to comments
- `TaskBreakdownTagProcessor` — Break epics into subtasks
- `DesignTagProcessor` — Design validation
- `EpicPlanningTagProcessor` — Epic planning
- `PrTagProcessor` — PR workflows
- `CreateBugTagProcessor` — Bug creation
- `SubtasksCompleteProcessor` — Check subtask completion

**ProcessorContext provides:**
- Issue data, AgentExecutor, BriefingBuilder
- CollabDocumentService, IssueLifecycleManager
- Helper: `evaluateContract(contract, briefing)` → parsed result

### 4.6 Lifecycle Manager

**File:** `taskorchestrator/.../executor/IssueLifecycleManager.java`

**Guard rules (prevent loops):**
```
NEEDS_DESIGN blocked by: DESIGN_DONE
NEEDS_TASK_BREAKDOWN blocked by: TASKS_CREATED
NEEDS_RESEARCH blocked by: RESEARCH_COMPLETE, PLAN_READY, PLAN_APPROVED
```

**Pair rules (done tag removes trigger):**
```
DESIGN_DONE removes NEEDS_DESIGN
TASKS_CREATED removes NEEDS_TASK_BREAKDOWN
RESEARCH_COMPLETE removes NEEDS_RESEARCH
```

**Transition outcomes:**
```java
enum TransitionOutcome {
    DESIGN_COMPLETE, BREAKDOWN_COMPLETE, RESEARCH_ONGOING,
    PLAN_READY, RESEARCH_COMPLETE, APPROVED_DONE,
    REVISION_READY, REVISION_ONGOING, COMMENTS_DONE,
    RETRY, ESCALATE, ERROR
}
```

### 4.7 Service Provider Config

**File:** `agentcontracts/.../ServiceProviderConfig.java`
**Storage:** `config/service/providers.json`

Maps contracts and issue tags to handler agents:
```java
class ServiceProviderConfig {
    Map<String, ContractProvider> issueContracts;
    // e.g., "issue-verdict" → {provider: "reviewer:jasmin", human: "human:engineer"}

    Map<String, TagContractMapping> tagContracts;
    // e.g., "#design" → {provider: ["designer:julia"], human: "human:engineer"}
}
```

---

## 5. Agent Engine

### 5.1 Agent Executor

**File:** `agentengine/.../engine/AgentExecutor.java`

**Agentic loop (MAX_ITERATIONS = 20):**

```
1. Resolve agent profile (role + persona)
2. Get allowed tools (auto-inject handoff_to_dispatcher for WORKERs)
3. Assemble system prompt via PromptAssembler
4. Build conversation: [system → history → user message]
5. Select model via ModelDispatcher
6. LOOP:
   a. Call LLM with tools
   b. Record InferenceAction (tokens, model, tool calls)
   c. If tool calls:
      - Execute each tool via ToolExecutor
      - If route_to_agent → execute target agent, return response
      - If handoff_to_dispatcher → return routing metadata
      - If ROBOT → return raw tool output (no second inference)
      - Else → add tool results to conversation, continue
   d. If no tool calls → return final text response
7. After 20 iterations → return summary
```

**Key methods:**
```java
String execute(RequestContext, String message)
AgentResponse executeWithRouting(RequestContext, String, AgentLlmContext)
<T> T executeWithContract(RequestContext, String, AgentContract<T>)
```

**AgentResponse:**
```java
class AgentResponse {
    String text;
    AgentProfileId routedAgentProfile;
    ClaudeCodeSessionId claudeCodeSessionId;
    boolean wasRouted();
}
```

### 5.2 Agent Contracts

**File:** `agentcontracts/.../AgentContract.java`

```java
interface AgentContract<T> {
    String name();
    String description();
    String prompt();           // injected into agent message
    String responseSchema();   // JSON schema for response
    T parse(String response);  // parse raw response to T
}
```

**Implementations:** `IssueVerdictContract`, `IssueCreateContract`, `IssueUpdateContract`, `IssueCommentContract`, `CollabCommentReplyContract`, `PullRequestContract`, `TaskBreakdownContract`, `EpicPlanningContract`

### 5.3 Execution Strategies

**File:** `taskorchestrator/.../executor/verdict/NormalExecutionStrategy.java`

**Flow:**
1. Validate issue (IN_PROGRESS)
2. Resolve agent profile
3. Setup session (new or reuse)
4. Add comment "Starting work..."
5. Build briefing → Execute agent
6. Record metadata (session, request IDs)
7. Build verdict briefing → Execute verdict contract
8. Apply verdict:
   - `DONE` → CLOSED
   - `RETRY` → PENDING + `#retried`
   - `ESCALATE` → NEEDS_REVIEW + `#humanreview`
   - `ERROR` → mark with reason

---

## 6. Tool System

### 6.1 Tool Interface

**File:** `tools/.../Tool.java`

```java
interface Tool {
    String name();
    String description();
    Map<String, Object> inputSchema();  // JSON Schema
    ToolResult execute(RequestContext context, Map<String, Object> input);
    ToolResult execute(RequestContext context, Map<String, Object> input, ToolStreamListener listener);
}
```

**ToolResult:**
```java
class ToolResult {
    boolean success;
    String output;
    String error;
    Map<String, String> executionMetadata;
}
```

### 6.2 Available Tools

| Category | Tools |
|---|---|
| **Dispatch** | `route_to_agent`, `handoff_to_dispatcher`, `list_agents`, `delegate_to_agent` |
| **File** | `read_file`, `write_file`, `edit_file`, `delete_file`, `list_files`, `batch_read_file` |
| **Git** | `git_status`, `git_diff`, `git_log`, `git_commit` |
| **Search** | `find_file`, `repo_index`, `search` |
| **Issue** | `create_issue`, `get_issue`, `query_issues`, `add_comment` |
| **Collab** | `read_document`, `reply_to_comment` |
| **Shell** | `shell_exec` |
| **Claude Code** | `claudecode` |

### 6.3 Tool Registry & Executor

```java
class ToolRegistry {
    Set<Tool> toolSet;  // injected via Guice Multibinder
    Optional<Tool> get(String name);
    Set<String> availableTools();
}

class ToolExecutor {
    ToolResult execute(RequestContext ctx, String toolName, Map<String, Object> input);
    ToolResult execute(RequestContext ctx, String toolName, Map<String, Object> input, ToolStreamListener listener);
}
```

---

## 7. LLM Integration

### 7.1 Provider Abstraction

**File:** `llm/.../LlmClient.java`

```java
interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
```

**RoutingLlmClient** — delegates to first matching `LlmProvider`:
```java
interface LlmProvider {
    String name();
    boolean supports(String model);
    LlmResponse complete(LlmRequest request);
}
```

### 7.2 Request/Response

```java
@Value @Builder class LlmRequest {
    String model;
    List<Message> messages;   // role: system/user/assistant
    double temperature;       // default: 0.7
    int maxTokens;            // default: 4096
    List<Map<String, Object>> tools;
}

@Value class LlmResponse {
    String content;
    String finishReason;
    Usage usage;              // promptTokens, completionTokens, totalTokens
    List<ToolCall> toolCalls; // id, name, arguments
}
```

### 7.3 Anthropic Provider

**File:** `llm/.../provider/anthropic/AnthropicProvider.java`

- Dual auth: **OAuth** (Claude Max/Pro, `sk-ant-oat` tokens) and **API Key**
- OAuth auto-refreshes from `~/.claude/.credentials.json`
- Handles retry-after (429, 529, 402)
- Maps tool names between LizzyCode and Claude Code canonical names in OAuth mode

**Credential resolution (priority):**
1. Claude Code OAuth credentials
2. Config `llm.anthropic.apiKey`
3. Env `ANTHROPIC_API_KEY`

---

## 8. Session Management

### 8.1 Session Tracking

**File:** `sessionmanager/.../SessionTracker.java`

```java
interface SessionTracker {
    SessionId createSession();
    RequestId startRequest(SessionId, String message);
    void completeRequest(SessionId, RequestId, String response);
    void recordInference(SessionId, RequestId, AgentProfileId, InferenceAction);
    void recordToolCall(SessionId, RequestId, AgentProfileId, ToolAction);
}
```

**TrackedSession → TrackedRequest → [InferenceAction | ToolAction]**

**InferenceAction** tracks: model, promptJson, responseContent, finishReason, token counts, tool calls requested

**ToolAction** tracks: toolName, input, success/error, durationMs, executionMetadata

### 8.2 Session Scope

**File:** `sessionmanager/.../SessionScope.java`

ThreadLocal context management:
```java
SessionId begin(IssueId)
RequestContext startRequest(SessionId, String message, AgentProfileId, IssueId)
void completeRequest(SessionId, RequestId, String response)
void end()  // clears ThreadLocal + MDC
```

**RequestContext** (flows through tool pipeline):
```java
class RequestContext {
    SessionId sessionId;
    AgentProfileId agentProfile;
    RequestId requestId;
    IssueId issueId;
}
```

---

## 9. Storage

**File:** `store/.../DocumentStore.java`

```java
interface DocumentStore {
    <T> Optional<T> get(String collection, String id, Class<T> type);
    <T> List<T> list(String collection, Class<T> type);
    <T> void put(String collection, String id, T document);
    void delete(String collection, String id);
}
```

**FileDocumentStore** — JSON files at `{dataDir}/{collection}/{id}.json`

---

## 10. Bootstrap

**File:** `runner/.../LizzyCodeRunner.java`

```
1. Load TypeSafe config
2. Create Guice Injector (AppModule)
3. Spawn IssueScheduler daemon thread
   - resetStuckIssues() (IN_PROGRESS → PENDING)
   - Register as IssueChangeListener
   - Start dispatch loop
4. Start LizzyCodeServer (Netty WebSocket)
```

---

## Example Workflow: Epic → Tasks → Completion

```
Epic created (BACKLOG)
  ↓ auto-assign to designer
  ↓ #needsresearch tag
  ↓ EpicPlanningTagProcessor runs
  ↓ Agent researches, creates stories/tasks
  ↓ TransitionOutcome.BREAKDOWN_COMPLETE
  ↓ #taskscreated tag, removes #needstaskbreakdown
  ↓
Stories with TASKs move to PENDING
  ↓ Scheduler dispatches to workers
  ↓ Each TASK: execute → verdict → CLOSED
  ↓
All subtasks CLOSED
  ↓ SubtasksCompleteProcessor detects
  ↓ #subtaskscomplete tag
  ↓ Story → NEEDS_REVIEW → #verified
```

---

## Key Configuration Files

| Path | Purpose |
|---|---|
| `config/agents/{roleId}/{roleId}.json` | Role definition |
| `config/agents/{roleId}/{personaId}.json` | Persona definition |
| `config/models/models.json` | Model aliases, buckets, tiers |
| `config/service/providers.json` | Contract → agent routing |
| `config/systemprompt/core/` | Core prompts (always included) |
| `config/systemprompt/capabilities/` | Capability prompts |
| `config/systemprompt/guardrails/` | Guardrail prompts |
