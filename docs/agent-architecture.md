# Agent Architecture

How agents are defined on disk, how they inherit from each other, how personas compose with them, and how requests move through the runtime to produce a response.

This is the applied companion to [`.troupeforge/DESIGN.md`](../.troupeforge/DESIGN.md) — where DESIGN.md lists every record and field, this doc walks through what actually happens when a request arrives.

## Mental model

A TroupeForge agent is **three layers fused at runtime**:

```
┌─────────────────────────────────────┐
│  Agent Profile  (runtime identity)  │
│  ┌───────────────┐  ┌────────────┐  │
│  │ ResolvedAgent │+ │  Persona   │  │
│  │               │  │            │  │
│  │ • capabilities│  │ • voice    │  │
│  │ • guardrails  │  │ • style    │  │
│  │ • tools       │  │ • sections │  │
│  │ • prompt tree │  │ • tiers    │  │
│  └───────────────┘  └────────────┘  │
│           ▲                ▲         │
│           │                │         │
│     inheritance      composition     │
│           │                │         │
└───────────┼────────────────┼─────────┘
            │                │
      AgentDefinition  PersonaDefinition
      (on disk, JSON)  (on disk, JSON)
```

- **`AgentDefinition`** — what the agent *is*. Defines capabilities, tools, guardrails, prompt sections, and what persona slots it exposes. Lives in `{agent-id}-agent.json`.
- **`PersonaDefinition`** — what the agent *sounds like* and how it approaches work. Fills the agent's persona slots with voice / strategy text, sets tone + verbosity, and declares which model tiers this persona may use. Lives in `personas/{persona-id}-persona.json` under the agent's folder.
- **`AgentProfile`** — the runtime object, keyed by `AgentProfileId(agentId, personaId)`. Built by the engine from a resolved agent plus a persona. Every running agent is a profile; persona is **not optional**.

An agent without a persona cannot run. The same agent with different personas is a different `AgentProfileId` — `researcher:guru` and `researcher:nick` share the same tools, capabilities, and guardrails but reason about problems very differently. One might be your deep-investigation persona, the other your fast-turnaround persona.

## Defining an agent

Agents live in a directory tree that mirrors the inheritance hierarchy:

```
config/agents/
├── root-agent.json                    # the ROOT agent (AgentId.ROOT)
├── researcher/
│   ├── researcher-agent.json          # parent: root — investigation & orchestration
│   └── personas/
│       ├── guru-persona.json          # deep, strategic, plans three steps ahead
│       └── nick-persona.json          # fast, scrappy, trims scope aggressively
├── architect/
│   ├── architect-agent.json           # parent: root — design & review
│   └── personas/
│       ├── martin-persona.json
│       └── sofia-persona.json
└── ...
```

A realistic agent definition (the `researcher` from `troupeforge-testconfig`):

```json
{
  "id": "researcher",
  "name": "Researcher Agent",
  "description": "Investigates, researches, and orchestrates work across the agent network",
  "type": "WORKER",
  "parent": "root",
  "capabilities": { "values": ["planning", "research"] },
  "guardrails":   { "values": ["no-code-changes"] },
  "tools":        { "values": ["delegate_to_agent", "list_agents"] },
  "contractCapabilities": { "values": ["research", "web-search"] },
  "allowedTiers": ["SIMPLE", "STANDARD", "ADVANCED", "COMPLEX", "EXPERT"],
  "promptSections": {
    "sections": [
      {
        "key": "researcher-identity",
        "content": [
          "You are a researcher and orchestrator.",
          "You receive objectives and drive them to completion by investigating, understanding the problem space, breaking work into tasks, and leveraging the agent network."
        ],
        "order": 100
      }
    ]
  }
}
```

Notice how this agent is a pure orchestrator — its tools list is just `delegate_to_agent` and `list_agents`. It cannot touch code or files directly; the `no-code-changes` guardrail reinforces that. The researcher advertises two contract capabilities (`research` and `web-search`), which is how other agents on the bus discover it by role rather than by id.

Points worth knowing:

- **`parent`** is required on every agent. Top-level agents use `"root"` — the hardcoded root (`AgentId.ROOT`) is its own parent, the single base case of the tree.
- **`allowedTiers`** constrains which model tiers *this agent* is willing to use, independently of the persona's own `allowedTiers`. The effective set is the intersection.
- **`capabilities` / `guardrails` / `tools` / `contractCapabilities` / `promptSections`** are all `InheritableSet`s. Omitting a field means "full inherit from parent." Supplying `{ "values": [...] }` is shorthand for `INHERIT + add these`. Use `{ "action": "REPLACE", ... }` only when you genuinely need to discard the parent — this is deliberately the noisier spelling because it is the riskier operation. `{ "action": "REMOVE", ... }` subtracts specific values from the inherited set.
- **`promptSections`** are keyed. A child agent can override a parent's `core-identity` section by defining a new section with the same `key` — the child wins. Sections are assembled in ascending `order`, so low numbers come first in the final prompt.
- **`personaSections`** (not shown above; see `root-agent.json`) declares the *slots* the agent exposes to personas. Each slot has a `key`, an `order`, and a `required` flag. A persona fills these slots via its own `sections` map — the keys must line up.

## Defining a persona

A persona lives under its agent's `personas/` folder. Example (`researcher/personas/guru-persona.json`):

```json
{
  "id": "guru",
  "displayName": "Guru",
  "description": "The mastermind. Works at the epic level — researches complex objectives, orchestrates the agent network.",
  "style": {
    "tone": "calm, authoritative, strategic, sees the whole board",
    "verbosity": "BALANCED"
  },
  "sections": {
    "persona-voice": [
      "You are calm, authoritative, and strategic.",
      "You see the whole board.",
      "Speak with the quiet confidence of someone who has already thought three steps ahead."
    ],
    "persona-strategy": [
      "Always check the current state first — review all context before deciding next action.",
      "First create research tasks to investigate unknowns, then create stories for actual work.",
      "Think about dependencies — what needs to happen first, what can be parallelized."
    ]
  },
  "importantInstructions": [
    "Never create stories based on assumptions — always research first",
    "You orchestrate through the agent network — you never implement yourself"
  ],
  "allowedTiers": ["STANDARD", "ADVANCED", "COMPLEX", "EXPERT"],
  "disabled": false
}
```

Two things to notice about how this persona interacts with its agent:

- **The `importantInstructions` reinforce the agent's design.** `researcher-agent.json` already gives this agent no file-editing tools, so "you never implement yourself" is not enforcing a new rule — it is explaining to the model *why* it was given only delegation tools. Personas routinely do this: agent JSON sets the hard constraints; the persona tells the LLM how to behave within them.
- **The persona's `allowedTiers` is narrower than the agent's.** The researcher agent allows everything from `SIMPLE` up through `EXPERT`. The `guru` persona restricts itself to `STANDARD`–`EXPERT` because this persona is intended for heavy planning work and should never be routed to a trivial-tier model. A different persona on the same agent (say, a `nick` persona for lightweight triage) could legitimately open up `SIMPLE` and `TRIVIAL` again. The effective set is the intersection of both lists.

How the pieces feed into the prompt:

| Persona field | What it produces | Assembly order |
|---|---|---|
| `sections.{key}` | Fills the agent's `personaSections[key]` slot | Uses the agent's declared `order` for that slot |
| `style` | Auto-generates a `persona-style` section describing tone / verbosity / emoji | `order = 900` |
| `additionalRules` | Rolled into a dedicated section for rules that don't fit a slot | Late |
| `importantInstructions` | Appended as the *absolute last* section | `order = 999` |

`importantInstructions` is reserved for non-negotiables you want the LLM to see last (last = most recent in attention). Personality belongs in `sections`, not here.

## Inheritance resolution

When a bucket loads, `AgentInheritanceResolver` walks the tree in topological order and, for each agent, resolves its fields against its already-resolved parent:

```
resolveList(parent, child):
  null           → parent values                  (full inherit)
  INHERIT        → parent ∪ child.values          (union)
  REPLACE        → child.values                   (discard parent)
  REMOVE         → parent \ child.values          (difference)

resolvePromptSections(parent, child):
  null           → parent sections
  INHERIT        → merge by key, child wins on collision, sort by order
  REPLACE        → child sections only
  REMOVE         → parent sections minus matching keys
```

The result is a `ResolvedAgent` — a fully-materialized view with every capability, guardrail, tool, contract capability, and prompt section pinned down. The raw `AgentDefinition` is never handed to the executor; only the resolved form is.

Persona composition happens **after** resolution (`PersonaComposer`): it takes the `ResolvedAgent` plus a `PersonaDefinition`, validates that the persona's `sections` keys match the agent's `personaSections` keys (required sections must be present, unknown keys log a warning), and produces the final `AgentProfile`.

A profile is immutable. Changing an agent or persona JSON requires a bucket reload — either at startup or via `BucketLifecycleServiceImpl.reload(bucketId)`.

## Orchestration: how a request becomes a response

All communication between agents — and between end-users and agents — flows through typed **contracts** on a **message bus**. There are no direct method calls between agents.

### Anatomy of a request

When a chat request hits `POST /api/chat`, `TroupeForgeEntryPointImpl` does the following:

1. **Build a `RequestContext`** — a first-class parameter containing `RequestId`, `UserId`, `OrganizationId`, `StageContext`, and `createdAt`. This flows explicitly through every subsequent call. There is no `ThreadLocal`.
2. **Resolve the `AgentBucketId`** — `OrganizationId + StageContext`. This is the primary isolation key; all downstream lookups (agents, contracts, models, sessions, usage events) are scoped to the bucket.
3. **Create or resume an `AgentSessionId`** via `AgentSessionFactory`. A new request gets a fresh session; a follow-up turn re-uses the session id from the previous response, and the executor loads the prior `AgentContext` from `InMemoryContextStore`.
4. **Look up the target `AgentProfile`** — either an explicit `(agentId, personaId)` in the request, or by picking the agent that provides the matching contract capability (e.g. `chat`).
5. **Wrap the payload in a `MessageEnvelope`** with `MessageType.REQUEST`, the resolved `ContractRef`, the `RequestContext`, and the sender's address. Hand it to the `MessageBus`.

### The agentic loop

The receiving agent's handler routes the envelope into `AgentExecutorImpl`. The loop (currently capped at **10** iterations via `MAX_LOOP_ITERATIONS`) is:

```
load or create AgentContext(sessionId)
assemble system prompt from AgentProfile.effectivePromptSections
select a model tier via ComplexityAnalyzer + allowedTiers intersection
for iteration in 1..MAX:
    response = llmProvider.complete(request)
    switch response.finishReason:
      STOP:
        → RESPOND: send REPLY envelope to effectiveReplyTo, save context, exit
      TOOL_USE:
        → execute each tool via ToolRegistry
        → inspect tool results:
            delegate_to_agent → DELEGATE_WAIT (see below)
            handover_to_agent → HANDOVER (see below)
            everything else   → append as tool-result message, CONTINUE
      MAX_TOKENS / ERROR:
        → send ERROR envelope, save context, exit
    save AgentContext after each iteration  (for debugging + resumption)
if iterations exhausted → send ERROR envelope
```

Each iteration persists the updated `AgentContext` to the context store before the next LLM call. A trace of iterations, tool calls, and token usage is captured in `ExecutionTrace` and returned alongside the response.

> **Note:** `DirectReturnPolicy` is a record on `AgentDefinition` but is **not yet wired** into the executor — today every response is synthesized by the LLM even when a tool result would already satisfy the contract's output schema.

### Delegation vs handover

Two different ways one agent can pull another into the conversation. Both go through the same message bus and contract system:

**`delegate_to_agent`** — the caller asks a peer for help and keeps control of the request.

Example: `researcher:guru` is investigating "should we move our ingest pipeline from SQS to Kafka?" It needs a load-and-cost analysis from an architect before it can draft recommendations, so it delegates to `architect:sofia`:

```
User ─────► researcher:guru (iteration 1)
                 │
                 └─► delegate_to_agent(
                         target   = "architect:sofia",
                         contract = "architecture-review",
                         payload  = { question: "...pipeline migration..." })
                         │
                         │  Engine creates a NEW AgentSessionId for sofia,
                         │  same RequestId, parentSessionId = sess-guru,
                         │  fresh empty AgentContext
                         │
                         ├─► REQUEST envelope to sofia, replyTo = null
                         │
                         │       architect:sofia runs its own loop in
                         │       sess-sofia, may itself delegate further,
                         │       eventually produces a REPLY envelope
                         │
                         ◄──── sofia's review: tradeoffs, cost estimate, risks
              researcher:guru (iteration 2)
                 │  ContextStore.load(sess-guru) restores guru's state
                 │  sofia's reply is injected as a tool-result message
                 └─► continues loop, writes final recommendation, RESPOND
User ◄──────── final reply from researcher:guru
```

The researcher's loop **blocks** on sofia's reply via a `CompletableFuture` with timeout inside `InMemoryMessageBus`. Guru never sees sofia's `AgentContext` or internal reasoning; it only receives the contract output that sofia produced.

**`handover_to_agent`** — the caller transfers the request entirely and exits. The receiving agent's reply goes directly to the original requester.

Example: the `root` dispatcher receives a chat turn and decides this is clearly a research task — there is no reason for root to stay in the loop and paraphrase the result. It hands the whole request over to `researcher:guru`:

```
User ─────► root:default (iteration 1)
                 │
                 └─► handover_to_agent(target = "researcher:guru")
                         │
                         │  Engine creates NEW AgentSessionId for guru,
                         │  HANDOVER envelope with replyTo = User
                         │
              root:default's loop TERMINATES here (terminal action)
                         │
                         │       researcher:guru runs its loop in sess-guru,
                         │       may delegate to architect / other workers,
                         │       produces the final answer
                         │
                         ▼
User ◄──────── researcher:guru's reply goes directly to the user
```

The receiving agent doesn't need to know which mode was used — it always sends its reply to `envelope.effectiveReplyTo()`. The mode only affects who holds the pen when the downstream agent finishes.

Rule of thumb: **delegate** when the caller still has work to do with the result. **Handover** when the caller was only routing and has nothing more to add.

### Session propagation

The invariant across any delegation chain:

> **Same `RequestId` · different `AgentSessionId`s · isolated `AgentContext`s**

One request threads through however many agents via a shared `RequestId`. Each agent gets its own `AgentSessionId` and its own `AgentContext` — state, conversation, and tool history are never shared. Parent→child links are recorded via `parentSessionId` on `AgentContext`, which means the full trace of `[sess-A, sess-B, sess-C, …]` for a given request can be reconstructed by querying `ContextStore.findByRequest(requestId)`.

This is what makes delegation safe. Agent B cannot read or corrupt Agent A's conversation; it only sees the message A explicitly sent. When A resumes after B's reply, its context is loaded fresh from the store and B's response is appended as a synthetic tool result.

## Where this lives in code

If you want to read the implementation alongside this doc:

| Concept | Code |
|---|---|
| Raw JSON → `AgentDefinition` | `troupeforge-engine`: `AgentConfigLoaderJsonImpl`, `PersonaConfigLoader` |
| Inheritance resolution | `AgentInheritanceResolverImpl` |
| Persona composition | `PersonaComposerImpl` |
| Prompt assembly | `PromptAssemblerImpl` |
| Model tier selection | `ModelSelectionServiceImpl`, `ComplexityAnalyzerImpl`, `ModelResolverImpl` |
| The agentic loop | `AgentExecutorImpl` (sync) and `StreamingAgentExecutor` (SSE) |
| Session store | `troupeforge-infra`: `InMemoryContextStore` |
| Message bus / delegation | `InMemoryMessageBus` — routing, `CompletableFuture` correlation |
| Delegation tools | `troupeforge-tools`: `DelegateToAgentTool`, `HandoverToAgentTool`, `ListAgentsTool` |
| REST entry point | `troupeforge-app`: `TroupeForgeEntryPointImpl`, `ChatController`, `StreamChatController` |

## Further reading

- [docs/modules.md](modules.md) — the full module map
- [docs/contributing.md](contributing.md) — step-by-step for adding a new agent, persona, or tool
- [.troupeforge/DESIGN.md](../.troupeforge/DESIGN.md) — authoritative design, with implementation-status callouts inline
- [.troupeforge/MULTI-TENANCY.md](../.troupeforge/MULTI-TENANCY.md) — how `AgentBucketId`, `RequestContext`, and `ContextStore` isolate organizations and stages
