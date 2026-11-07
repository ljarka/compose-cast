#!/usr/bin/env bash
# Keeps the agent documentation consistent (ADR 0009). Runs in CI.
#  - every Gradle module has an AGENTS.md
#  - relative links in Markdown files point to existing files
#  - every ADR in docs/decisions is listed in docs/decisions/README.md
#  - every playbook / task is listed in its folder README
#  - root AGENTS.md stays an index (< 150 lines)
#  - every open README roadmap item in a finished (✅) or active (⏳) Etap names its task (T-NNN),
#    so nothing planned is invisible to agents (they take work only from docs/tasks)
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

for folder, pattern in (("docs/playbooks", "*.md"), ("docs/tasks", "T-[0-9][0-9][0-9]-*.md")):
    index_file = root / folder / "README.md"
    listed = index_file.read_text() if index_file.is_file() else ""
    for f in sorted((root / folder).glob(pattern)):
        if f.name != "README.md" and f.name not in listed:
            errors.append(f"{folder}/{f.name} is not listed in {folder}/README.md")

etap = None
for line in (root / "README.md").read_text().splitlines():
    if line.startswith("### "):
        etap = line if ("✅" in line or "⏳" in line) else None
    elif etap and line.startswith("- [ ]") and not re.search(r"\bT-\d{3}\b", line):
        errors.append(f"README roadmap: open item without a task (add T-NNN) in '{etap[4:]}': {line[:70]}")

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
