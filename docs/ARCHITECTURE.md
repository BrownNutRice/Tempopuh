# Repository architecture

## Top-level ownership

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/me/avinas/tempo/` | Android production source |
| `app/src/main/res/` | Strings, icons, layouts and Android configuration resources |
| `app/src/test/` | Local JVM tests and fixtures |
| `app/src/androidTest/` | Android/Room/platform integration tests |
| `app/schemas/` | Room schema history; preserve for migration validation |
| `browser-extension/src/` | Content probes, tracking/sync background code and popup UI |
| `gradle/` | Wrapper, toolchain and dependency catalog |
| `.github/` | Manual build/validation workflows, templates and maintenance scripts |
| `docs/` | Current guides, references, audit records and release notes |

## Android package map

| Package | Boundary |
| --- | --- |
| `ui/` | Compose screens/components, appearance/theme and screen ViewModels |
| `data/local/` | Room entities, DAOs, migrations and local query helpers |
| `data/repository/` | Persistence orchestration, statistics/caches and refresh coordination |
| `data/remote/`, provider packages under `data/` | Provider auth/APIs, imports and enrichment |
| `domain/` | Existing insight/domain logic |
| `service/`, `receiver/` | Playback observation, session recovery and platform entry points |
| `worker/` | Deferrable import/enrichment/backup/maintenance work |
| `desktop/` | Phone-side companion pairing/discovery/HTTP ingestion |
| `widget/` | Home-screen widget rendering/update work |
| `di/`, `utils/` | Dependency wiring and shared helpers |

The current tree is an incremental MVVM/repository architecture, not a completed strict module split. Keep provider logic out of Composables and blocking I/O out of lifecycle callbacks. Reuse current repositories before introducing parallel implementations.

## Change boundaries

- Treat package/application ID, OAuth callbacks, Room file/schema, serialization keys and signing identity as compatibility contracts.
- Keep source observations separate from committed listening events and imported estimates.
- Make writes durable before acknowledging them; performance improvements must preserve pending events.
- Scope screen work to visibility and coordinate background ownership. See the [performance plan](https://github.com/matchalattewithbanana/Tempo/issues/85).
- Use theme roles and shared accessible controls. See the [UI audit](https://github.com/matchalattewithbanana/Tempo/issues/68) and [Profile proposal](https://github.com/matchalattewithbanana/Tempo/issues/74).

## Organization backlog

Large tracking, statistics, import and Profile files should be split by responsibility through focused follow-ups with migration/API and behavior tests. This maintenance pass establishes the map and contributor boundaries; it does not claim those runtime refactors are complete.

Generated `build/`, `.gradle/`, `node_modules/` and extension `dist/` output remain outside version control. Historical audits describe their original revision and are not current acceptance evidence.
