# server-app — AGENTS.md

Ktor HTTP server. Wires routing; screen definitions live in `Screens.kt`.

## Scope

This module only. Load `sdk-server/AGENTS.md` for DSL docs.

## Key Files

| File | Purpose |
|---|---|
| `Main.kt` | Ktor engine + ContentNegotiation setup |
| `Routing.kt` | Route handlers; reads from `registry` |
| `Screens.kt` | Screen + brand definitions using `composeCast {}` DSL |

## Add a New Screen

Edit `Screens.kt`, add a `screen("id") { ... }` block inside `composeCast {}`.

## Run

```bash
./gradlew :server-app:run
curl http://localhost:8080/health
curl http://localhost:8080/screens/main
curl http://localhost:8080/brand
curl http://localhost:8080/screens
```

## Dependencies

- `:protocol`
- `:sdk-server`
