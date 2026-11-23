#!/usr/bin/env bash
# Architecture rules as executable checks, so agents cannot break them silently. Runs in CI.
# Each rule names the decision it enforces. To change a rule: write/supersede the ADR first,
# then update the rule here in the same change.
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 - <<'PY'
import pathlib, re, sys

root = pathlib.Path(".")
SKIP = {"build", ".gradle", ".git", ".idea", ".kotlin"}
def files(pattern):
    return [p for p in root.rglob(pattern) if not SKIP.intersection(p.parts)]
def rel(p): return p.as_posix()

errors = []
def fail(rule, adr, where, msg, fix):
    errors.append(f"[{rule}] {where}: {msg}\n      why: {adr}\n      fix: {fix}")

kotlin = files("*.kt")
gradle = files("build.gradle.kts")
catalog = (root / "gradle/libs.versions.toml").read_text()

# R1 — Remote Compose API only in the renderer package (server) and sdk-android main sources.
R1_ALLOWED = [
    re.compile(r"^sdk-server/src/main/kotlin/io/composecast/server/sdk/render/[^/]+\.kt$"),
    re.compile(r"^sdk-android/src/main/"),
]
for f in kotlin:
    for n, line in enumerate(f.read_text().splitlines(), 1):
        if re.match(r"\s*import\s+androidx\.compose\.remote\.", line) and not any(r.search(rel(f)) for r in R1_ALLOWED):
            fail("R1", "docs/decisions/0002-confine-restricted-remote-compose-api.md", f"{rel(f)}:{n}",
                 "Remote Compose API used outside the allowed files",
                 "go through the renderer (sdk-server) or sdk-android public API instead")

# R2 — Remote Compose Gradle dependencies only in sdk-server and sdk-android.
for f in gradle:
    if "libs.remote." in f.read_text() and f.parent.name not in {"sdk-server", "sdk-android"}:
        fail("R2", "docs/decisions/0002-confine-restricted-remote-compose-api.md", rel(f),
             "depends on Remote Compose directly", "depend on sdk-server / sdk-android instead")

# R3 — No Remote Compose type in a non-internal declaration signature of sdk-android.
for f in [p for p in kotlin if rel(p).startswith("sdk-android/src/main/")]:
    text = f.read_text()
    rc_names = set(re.findall(r"^\s*import\s+androidx\.compose\.remote\.[\w.]*\.(\w+)\s*$", text, re.M))
    for n, line in enumerate(text.splitlines(), 1):
        decl = re.match(r"\s*((?:public|protected|override|open|data|abstract|suspend|inline)\s+)*(fun|val|var|class|interface|object|typealias)\b", line)
        if not decl or re.match(r"\s*(private|internal)\b", line):
            continue
        signature = re.split(r"\s=\s|\{", line, maxsplit=1)[0]
        leaked = [name for name in rc_names if re.search(rf"\b{name}\b", signature)]
        if leaked:
            fail("R3", "docs/decisions/0002-confine-restricted-remote-compose-api.md", f"{rel(f)}:{n}",
                 f"public signature exposes Remote Compose type(s) {leaked}",
                 "make it internal/private or wrap the type (like ScreenDocument wraps RemoteDocument)")

# R4 — protocol stays pure: no Remote Compose, no Android, no Ktor.
pb = (root / "protocol/build.gradle.kts").read_text()
for bad in ("libs.remote.", "android", "ktor"):
    if bad in pb:
        fail("R4", "protocol/AGENTS.md (shared types only)", "protocol/build.gradle.kts",
             f"protocol depends on '{bad}'", "keep protocol a plain Kotlin/JVM module of data types and constants")

# R5 — AGP 9 built-in Kotlin: never apply org.jetbrains.kotlin.android.
for f in gradle + [root / "gradle/libs.versions.toml"]:
    t = f.read_text()
    if "org.jetbrains.kotlin.android" in t or "libs.plugins.kotlin.android" in t:
        fail("R5", "AGENTS.md → Environment (AGP 9 built-in Kotlin)", rel(f),
             "kotlin-android plugin referenced", "remove it; AGP 9 compiles Kotlin itself")

# R6 — Compose versions only from the BOM.
for f in gradle:
    t = f.read_text()
    uses_compose = re.search(r"libs\.compose\.(?!bom)", t)
    if uses_compose and "platform(libs.compose.bom)" not in t:
        fail("R6", "AGENTS.md → Environment (Compose via BOM)", rel(f),
             "uses Compose libraries without platform(libs.compose.bom)", "add platform(libs.compose.bom) to the same configuration")
for n, line in enumerate(catalog.splitlines(), 1):
    if re.search(r'module\s*=\s*"androidx\.compose\.(ui|runtime|foundation|material3|animation)[^"]*"', line) and "version" in line:
        fail("R6", "AGENTS.md → Environment (Compose via BOM)", f"gradle/libs.versions.toml:{n}",
             "Compose library with an explicit version", "drop the version; the BOM provides it")

# R7 — All dependency versions live in the version catalog.
for f in gradle:
    for n, line in enumerate(f.read_text().splitlines(), 1):
        if re.search(r'(implementation|api|testImplementation|debugImplementation|classpath)\s*\(\s*"[^":]+:[^":]+:[^"]+"', line):
            fail("R7", "AGENTS.md → Environment (versions in gradle/libs.versions.toml)", f"{rel(f)}:{n}",
                 "hard-coded dependency coordinates", "add it to gradle/libs.versions.toml and use libs.*")

# R8 — server-scripting stays free of Ktor (server-app depends on it).
if "ktor" in (root / "server-scripting/build.gradle.kts").read_text():
    fail("R8", "server-scripting/AGENTS.md", "server-scripting/build.gradle.kts",
         "server-scripting depends on Ktor", "keep HTTP concerns in server-app")

# R9 — every Gradle module has an AGENTS.md of at most 150 lines (agents load one module at a time).
ADR23 = "docs/decisions/0023-module-structure.md"
# Modules = top-level directories with a build file (distribution/ and examples/ are app templates, not modules).
modules = sorted({root / p.parts[0] for p in gradle if len(p.parts) > 1 and p.parts[0] not in {"distribution", "examples"}})
for m in modules:
    a = m / "AGENTS.md"
    if not a.exists():
        fail("R9", ADR23, rel(m), "module has no AGENTS.md", "add one (scope, files, public API, decisions, tests)")
    elif len(a.read_text().splitlines()) > 150:
        fail("R9", ADR23, rel(a), "longer than 150 lines", "move detail to docs/ and link it")

# R10 — feature modules never depend on server-app or on each other (server-admin may use features).
FEATURE = re.compile(r"^server-(?!app$|scripting$)[\w-]+$")
for f in gradle:
    name = f.parent.name
    if not FEATURE.match(name):
        continue
    for dep in re.findall(r'project\(\s*"[:]([\w-]+)"\s*\)', f.read_text()):
        bad = dep == "server-app" or (FEATURE.match(dep) and dep != "server-store" and name != "server-admin")
        if bad:
            fail("R10", ADR23, rel(f), f"feature module '{name}' depends on '{dep}'",
                 "depend only on protocol, sdk-server, server-store; wire features together in server-app")

# R11 — the server image copies every module server-app needs (transitively), or the build fails in Docker only.
def project_deps(name, seen):
    bf = root / name / "build.gradle.kts"
    for dep in re.findall(r'project\(\s*"[:]([\w-]+)"\s*\)', bf.read_text() if bf.exists() else ""):
        if dep not in seen:
            seen.add(dep)
            project_deps(dep, seen)
    return seen
dockerfile = (root / "server-app/Dockerfile").read_text()
for dep in sorted(project_deps("server-app", {"server-app"})):
    if not re.search(rf"^COPY {re.escape(dep)} {re.escape(dep)}$", dockerfile, re.M):
        fail("R11", "server-app/AGENTS.md → Docker", "server-app/Dockerfile",
             f"module '{dep}' is not copied into the build stage", f"add 'COPY {dep} {dep}'")

if errors:
    print(f"Architecture check failed ({len(errors)}):")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"Architecture OK: 11 rules, {len(kotlin)} Kotlin files, {len(gradle)} build files")
PY
