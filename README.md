# TroupeForge

> Java 21 · Spring Boot 3.4 · Gradle (Kotlin DSL) · Contract-based multi-agent orchestration

TroupeForge is a framework for building and running collaborating LLM agents. Agents inherit
from a configurable tree, communicate over typed contracts, and execute tools through a
pluggable infrastructure layer.

## Modules

| Module | Purpose |
|---|---|
| `troupeforge-core` | Domain records, interfaces, SPI — zero Spring |
| `troupeforge-engine` | Agent executor, orchestration, session tracking |
| `troupeforge-infra` | LLM providers, storage backends, messaging |
| `troupeforge-tools` | Built-in agent tool implementations |
| `troupeforge-app` | Spring Boot entry point and programmatic API |
| `troupeforge-testconfig` | Sample configs and fixtures for integration tests |

## Highlights

- **Agent inheritance tree** — every agent extends a parent, with explicit
  `INHERIT` / `REPLACE` / `REMOVE` directives on list fields.
- **Personas as execution units** — required style/tone overlay; carries the model strategy.
- **Pluggable LLM providers** — Anthropic Claude provider included; SPI for adding more.
- **Pluggable storage** — filesystem store out of the box.
- **Tool system** — file, shell, web, git, reasoning and dispatch tools ship in `troupeforge-tools`.
- **Multi-tenant ready** — see `.troupeforge/MULTI-TENANCY.md`.

## Build

```bash
./gradlew build
```

## Run

```bash
./gradlew :troupeforge-app:bootRun
```

A bundled chat client lives in `troupeforge-client`:

```bash
troupeforge-client/troupeforge-chat.cmd   # Windows
troupeforge-client/troupeforge-chat.ps1   # PowerShell
```

## Documentation

User-facing guides live under [`docs/`](docs):

- [docs/getting-started.md](docs/getting-started.md) — build, configure, run, first chat request
- [docs/modules.md](docs/modules.md) — what each Gradle module contains
- [docs/contributing.md](docs/contributing.md) — conventions and how to add agents, personas, tools, providers

Architecture design notes live under [`.troupeforge/`](.troupeforge):

- [DESIGN.md](.troupeforge/DESIGN.md) — full architecture (with current implementation status inline)
- [MULTI-TENANCY.md](.troupeforge/MULTI-TENANCY.md) — bucket / org / stage isolation model

## License

See [LICENSE](LICENSE).
