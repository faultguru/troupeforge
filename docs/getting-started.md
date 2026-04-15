# Getting Started

This guide walks through building TroupeForge, running the app against the bundled sample config, and sending a first chat request.

## Prerequisites

- **JDK 21** (records + virtual threads are required)
- An **Anthropic API key** or a Claude OAuth credential file at `~/.claude/.credentials.json`
- Windows users: the commands below assume PowerShell / cmd. Use `./gradlew` on Linux / macOS.

## 1. Build

```bash
./gradlew build
```

This builds all modules (`troupeforge-core`, `-engine`, `-infra`, `-tools`, `-app`, `-client`, `-testconfig`, `-tests`, `-integtests`) and runs unit tests.

## 2. Configure credentials

`ClaudeLlmProvider` resolves credentials in this order:

1. OAuth token from `~/.claude/.credentials.json` (auto-refreshed on expiry)
2. `ANTHROPIC_API_KEY` environment variable
3. `troupeforge.llm.api-key` in `application.yml` / `application-local.yml`

Set whichever you have:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

## 3. Run the app

```bash
./gradlew :troupeforge-app:bootRun
```

The app binds to `http://localhost:8080`. At startup, `BucketAutoLoadConfig` reads `troupeforge-testconfig/src/main/resources/config` and registers a default bucket under organization id `default` — see `troupeforge-app/src/main/resources/application.yml` to point it elsewhere.

You should see logs like:

```
BucketAutoLoadConfig — loaded bucket default:live with N agents
TroupeForgeApplication — Started in X seconds
```

## 4. Send a chat request

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "u1",
    "organizationId": "default",
    "sessionId": null,
    "agentId": "greeter",
    "personaId": "bond",
    "message": "hello"
  }'
```

The response is a `ChatResponse` JSON object with the agent's reply, token usage, inference trace, and the `sessionId` to reuse for follow-up turns.

For streaming (SSE):

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{ ...same body... }'
```

## 5. Use the CLI client

`troupeforge-client` is a small REPL that talks to the REST API.

```bash
# Windows
troupeforge-client/troupeforge-chat.cmd
# PowerShell
troupeforge-client/troupeforge-chat.ps1
```

It handles session persistence and ANSI formatting for local dev.

## Where to go next

- [modules.md](modules.md) — what each Gradle module does and how they depend on each other
- [contributing.md](contributing.md) — coding conventions and how to add a new agent, persona, tool, or LLM provider
- [../.troupeforge/DESIGN.md](../.troupeforge/DESIGN.md) — full architecture design doc
- [../.troupeforge/MULTI-TENANCY.md](../.troupeforge/MULTI-TENANCY.md) — how bucket isolation works

## Known limitations

- Document and context stores are **in-memory only** — sessions do not survive a restart.
- Only the Anthropic (Claude) LLM provider ships.
- Hand-rolled in-memory message bus; no Redis / Kafka backend yet.
- Agentic loop caps at 10 iterations.
- `DirectReturnPolicy` records exist but are not enforced — every response is synthesized by the LLM.
