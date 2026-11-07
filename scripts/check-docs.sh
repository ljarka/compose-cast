#!/usr/bin/env bash
# Keeps the agent documentation consistent (ADR 0009). Runs in CI.
#  - every Gradle module has an AGENTS.md
#  - relative links in Markdown files point to existing files
#  - every ADR in docs/decisions is listed in docs/decisions/README.md
#  - root AGENTS.md stays an index (< 150 lines)
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 - <<'PY'
import pathlib, re, sys

root = pathlib.Path(".")
errors = []

modules = re.findall(r'include\(":([^"]+)"\)', (root / "settings.gradle.kts").read_text())
for m in modules:
    if not (root / m / "AGENTS.md").is_file():
        errors.append(f"module '{m}' has no AGENTS.md")

skip = {"build", ".gradle", ".git", ".idea", ".kotlin", "node_modules"}
md_files = [p for p in root.rglob("*.md") if not skip.intersection(p.parts)]
link = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
for md in md_files:
    text = re.sub(r"```.*?```", "", md.read_text(), flags=re.S)  # ignore code blocks
    for target in link.findall(text):
        if re.match(r"^[a-z]+:", target) or target.startswith("#"):
            continue
        path = (md.parent / target.split("#", 1)[0]).resolve()
        if not path.exists():
            errors.append(f"{md}: broken link -> {target}")

decisions = root / "docs" / "decisions"
index = (decisions / "README.md").read_text()
for adr in sorted(decisions.glob("[0-9][0-9][0-9][0-9]-*.md")):
    if adr.name not in index:
        errors.append(f"ADR {adr.name} is not listed in docs/decisions/README.md")

lines = len((root / "AGENTS.md").read_text().splitlines())
if lines >= 150:
    errors.append(f"AGENTS.md has {lines} lines; keep it an index (< 150), move details out")

if errors:
    print("Documentation check failed:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"Documentation OK: {len(modules)} modules, {len(md_files)} Markdown files, AGENTS.md {lines} lines")
PY
