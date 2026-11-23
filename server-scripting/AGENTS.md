# server-scripting — AGENTS.md

Planned: host for `.screen.kts` scripts so screens can be edited and hot-reloaded without
restarting the server (docs/feature-checklist.md, dev loop). **Empty module** — only `build.gradle.kts`.

## Constraints for whoever starts it

- Scripts must produce the same internal screen model as the Kotlin DSL and the admin panel
  ([ADR 0008](../docs/decisions/0008-server-side-data-sources.md), [ADR 0015](../docs/decisions/0015-screen-model-is-the-product.md)).
- `server-app` depends on this module; keep it free of Ktor.
