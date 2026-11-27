# T-055 — Static analysis (`detekt`) and `explicitApi()` for published libraries

Status: done · Roadmap: quality · Depends on: — · Module: protocol, sdk-server, sdk-android

## Goal

Enforce explicit visibility on published library modules (`protocol`, `sdk-server`, `sdk-android`)
and add `detekt` static analysis to `./scripts/verify.sh`.

## Why

- `protocol`, `sdk-server`, and `sdk-android` are published as Maven artifacts (`io.composecast:*`).
  Without `explicitApi()`, helper classes and functions can accidentally leak into the public API.
- Spotless/ktlint enforces formatting only; `detekt` catches complexity, bug patterns, and unused
  code across JVM and Android Kotlin modules.

## Design

- Enable `explicitApi()` in `protocol`, `sdk-server`, and `sdk-android` and add explicit `public` /
  `internal` modifiers where missing.
- Configure `detekt` in the root build (version in `gradle/libs.versions.toml`, rules in
  `config/detekt/detekt.yml`) and run `detekt` from `scripts/verify.sh`.

## Steps (files to touch)

1. `protocol/build.gradle.kts`, `sdk-server/build.gradle.kts`, `sdk-android/build.gradle.kts`:
   enable `explicitApi()` and annotate public declarations.
2. `gradle/libs.versions.toml`, `build.gradle.kts`, `config/detekt/detekt.yml`, `scripts/verify.sh`:
   configure and run `detekt`.

## Acceptance criteria

- `protocol`, `sdk-server`, and `sdk-android` compile with `explicitApi()`.
- `./scripts/verify.sh` runs `detekt` and passes cleanly.

## Verification

`./scripts/verify.sh`

## Docs to update

`AGENTS.md`, `docs/history.md`.

## Out of scope

Binary Compatibility Validator dump files (can be added once 1.0 API stabilizes).
