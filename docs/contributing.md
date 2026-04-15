# Contributing

## Coding conventions

- **Java 21** — records for all data, sealed interfaces where a closed set makes sense, virtual threads for I/O-bound work.
- **No Lombok.** Records cover the data classes. Plain classes stay plain.
- **Typed IDs.** Never pass raw `String` ids across a public boundary — wrap them in the existing typed records (`AgentId`, `PersonaId`, `ToolId`, etc.).
- **JSON, not YAML**, for all domain config (agents, personas, contracts, models). Spring config in `application.yml` is the exception — it is Spring's own config, not TroupeForge config.
- **Explicit `RequestContext`** — never introduce a `ThreadLocal`. The context flows as a parameter from the entry point through to the LLM call and the tools.
- **No `null` in domain records** for list fields — use an empty `InheritableSet` or empty list. Nullability is allowed only where the design doc explicitly calls it out (e.g. `parentSessionId` for root sessions).
- **Comments:** only when the *why* is not obvious from the code.

## Module boundaries

| You are adding… | Put it in |
|---|---|
| A new domain record, ID type, or SPI interface | `troupeforge-core` |
| A new loader, resolver, executor, or prompt pipeline step | `troupeforge-engine` |
| A new LLM provider, storage backend, or message bus backend | `troupeforge-infra` |
| A new `Tool` implementation | `troupeforge-tools` |
| A new REST endpoint or Spring wiring | `troupeforge-app` |
| A sample agent / persona / contract for tests | `troupeforge-testconfig` |

`troupeforge-core` has no dependencies other than Jackson annotations — keep it that way. If you reach for Spring in `-core`, the design has already gone wrong.

## Adding an agent

1. Decide its parent. Top-level agents parent on `AgentId.ROOT`.
2. Create the folder `{config-root}/agents/{parent-chain}/{agent-id}/`.
3. Add `{agent-id}-agent.json` — see `troupeforge-testconfig` for examples. Use `InheritableSet` shorthand (omit a field entirely for full inherit; supply `{ "values": [...] }` for inheriting + adding; supply `{ "action": "REPLACE", ... }` only when you truly need to discard the parent).
4. Add `personas/` with at least one persona JSON. Persona is **required** — an agent without a persona cannot run.
5. Restart the app (or call the lifecycle reload API) and verify the agent shows up via `GET /api/agents`.

## Adding a persona

A persona lives under an agent's `personas/` subfolder. It fills `personaSections` slots defined by the agent, and declares which model `TierId`s it can use:

```json
{
  "id": "my-persona",
  "displayName": "Example Persona",
  "style": { "tone": "direct", "verbosity": "CONCISE", "emoji": "", "greeting": "" },
  "sections": { "persona-voice": "You are…" },
  "additionalRules": [],
  "allowedTiers": ["SIMPLE", "STANDARD"]
}
```

The persona's `allowedTiers` is a flat list of `TierId` values. There is no `defaultTier` / `maxTier` today — the earlier design's `ModelAccessConfig` was simplified (see `.troupeforge/DESIGN.md` §2.9).

## Adding a tool

1. Create a Java record for the tool's input with `@ToolParam` annotations on each field — `ToolSchemaGenerator` uses these to produce the JSON schema advertised to the LLM.
2. Create a record for the output.
3. Implement `Tool`:

    ```java
    public final class MyTool implements Tool<MyInput, MyOutput> {
        public String name() { return "my_tool"; }
        public String description() { return "..."; }
        public MyOutput execute(ToolContext ctx, MyInput input) { ... }
    }
    ```

4. Register it as a Spring bean under `troupeforge-tools` (or add it to an existing `@Configuration`).
5. Add the `ToolId` to whichever agents should inherit / receive it. Use `InheritableSet` semantics — prefer extending via `{ "values": ["my_tool"] }` on a child agent rather than `REPLACE` on the parent.

Tool execution runs on the same thread as the agentic loop. If your tool blocks on I/O, use virtual threads inside it so the scheduler stays responsive.

## Adding an LLM provider

1. Implement `LlmProvider` in `troupeforge-infra`. The interface covers synchronous `complete()` plus optional `stream()`.
2. Add a matching `LlmProviderFactory` so `ProviderConfigLoader` can wire it from JSON.
3. Provide a sample provider JSON under `troupeforge-testconfig/src/main/resources/config/models/providers/` for integration tests.
4. Handle retry, rate limiting, and credential resolution in the provider itself — there is no generic retry decorator yet (see `ClaudeLlmProvider` for the shape to follow).

## Adding a storage backend

The goal state is a pluggable `DocumentStoreFactory` + `ContextStore` pair selected via `troupeforge.storage.type`. Today only `InMemoryDocumentStore` / `InMemoryContextStore` exist. If you add a real backend (filesystem, Postgres, DynamoDB, …):

1. Implement `DocumentStoreFactory` and `ContextStore` in `troupeforge-infra`.
2. Route all reads / writes through `AgentBucketId` — cross-bucket access is a bug, not a missing feature.
3. Use `StorageResult.version()` for optimistic locking; throw on version mismatch.
4. Add a `@ConditionalOnProperty` so the bean only activates when the matching config is set.
5. Update `docs/getting-started.md`'s "Known limitations" section.

## Running tests

```bash
./gradlew test                  # unit tests in every module
./gradlew :troupeforge-tests:test
./gradlew :troupeforge-integtests:test   # spins up the full app
```

`-integtests` uses the sample config under `troupeforge-testconfig` — keep that tree realistic when you add new features.

## Commits and PRs

- One logical change per commit. Bug fixes, refactors, and new features go in separate commits.
- Commit messages explain **why**, not what — the diff already shows what.
- Do not introduce backwards-compat shims for internal APIs. This codebase has no external consumers yet; rename freely.
- If you change something that the design docs describe, update `.troupeforge/DESIGN.md` (or `MULTI-TENANCY.md`) in the same commit. The docs and code must not drift.
