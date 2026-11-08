# Playbook — upgrade dependencies (Compose, Kotlin, Ktor, AGP, …)

Remote Compose has its own playbook ([upgrade-remote-compose.md](upgrade-remote-compose.md)).

1. **Latest stable versions**
   ```bash
   latest() { curl -sf "$1" | grep -o '<version>[^<]*' | sed 's/<version>//' \
     | grep -viE 'alpha|beta|rc|dev|eap|M[0-9]' | tail -1; }
   G=https://dl.google.com/android/maven2; M=https://repo1.maven.org/maven2
   latest $G/androidx/compose/compose-bom/maven-metadata.xml
   latest $G/com/android/tools/build/gradle/maven-metadata.xml
   latest $M/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml
   latest $M/io/ktor/ktor-server-core/maven-metadata.xml
   ```
2. **AGP**: read its release notes for the minimum Gradle/KGP/JDK and max API level; update the
   wrapper twice (`./gradlew wrapper --gradle-version X`, then again so scripts and jar update),
   and bump the `gradle:X-jdk21-noble` build image in `server-app/Dockerfile` to the same version
   (`verify.sh --docker` fails on a mismatch).
3. Bump in `gradle/libs.versions.toml` only (rules R6/R7 enforce catalog + Compose BOM).
   New libraries may require a higher `compileSdk`.
4. `./scripts/verify.sh`. If lint got quieter, prove checks still run: add a deliberately wrong
   `produceState` (never assigns `value`) in `app-template`, run `:app-template:lintDebug`, expect
   `ProduceStateDoesNotAssignValue`, delete it. (Compose 1.7.5 under AGP 9.5 ran no checks silently.)
5. Screenshot diffs: compare RC content vs frame (see upgrade-remote-compose step 4).
6. [verify-on-emulator.md](verify-on-emulator.md).
7. Docs: versions in root `AGENTS.md`, `docs/history.md`; ADR if a decision changed.
