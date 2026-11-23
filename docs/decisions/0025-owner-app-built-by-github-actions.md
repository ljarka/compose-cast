# 0025 — The owner's app is built by GitHub Actions in its exported repository

- Status: Accepted (owner, 2026-11-23)
- Date: 2026-11-23
- Refines: [0017](0017-apps-in-own-repositories.md), T-010 (app export)

## Context

The plan for Etap 9 ended with "unzip the exported project and build it in Android Studio". The
primary user is a non-developer ([specs/mission.md](../../specs/mission.md)); success means a
signed app in Google Play **without installing Android Studio**. Options: (a) build on the server
(Android SDK + Gradle in the image: multi-GB image, heavy VPS, slow), (b) the owner builds locally,
(c) a hosted CI builds the exported repository.

## Decision

(c): the admin panel exports an **app repository** (ADR 0017 layout: `compose-cast.properties`,
icon, wrapper, `.gitignore`) **plus a GitHub Actions workflow** that builds a signed AAB with the
`io.composecast.app` plugin. The keystore and its passwords are GitHub secrets
(`COMPOSECAST_SIGNING_STORE_PASSWORD`, `COMPOSECAST_SIGNING_KEY_PASSWORD`, keystore as a base64
secret), consumed by T-044's signing keys. The server never builds APKs.

## Consequences

- The owner needs a GitHub account; guided docs cover creating the keystore, the secrets and the
  Play upload (roadmap E1–E3).
- The workflow template lives in `distribution/android-app/` and is verified by
  `scripts/verify-app-repository.sh`.
- The VPS stays small; the server image does not contain the Android SDK.

## How to verify it still holds

Pushing an exported repository to a new GitHub repository with the secrets set produces a signed
AAB artifact (`apksigner`/`bundletool` verify) without any local build.
