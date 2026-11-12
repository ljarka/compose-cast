# Architecture Decision Records

One decision per file: why the code is the way it is. Read the ADRs that touch the area you are
changing **before** changing it; if you change a decision, write a new ADR that supersedes the old
one (never silently contradict an accepted ADR).

| ADR | Title | Status |
|---|---|---|
| [0001](0001-remote-compose-wire-format.md) | Remote Compose is the Android wire format and renderer | Accepted |
| [0002](0002-confine-restricted-remote-compose-api.md) | Confine the restricted Remote Compose API | Accepted |
| [0003](0003-embed-images-server-side.md) | Embed images in documents on the server | Accepted |
| [0004](0004-units-dp-and-sp.md) | Units: DP density behavior + sp font expressions | Accepted |
| [0005](0005-player-view-wrapper.md) | Wrap `remote-player-view` instead of `remote-player-compose` | Accepted |
| [0006](0006-screenshot-tests-as-goldens.md) | Screenshot tests are the golden compatibility tests | Accepted |
| [0007](0007-ios-strategy-spike.md) | iOS: decide by spike between RC player and DivKit | Proposed |
| [0008](0008-server-side-data-sources.md) | External APIs are bound on the server (data sources) | Accepted |
| [0009](0009-documentation-for-agents.md) | Documentation structure for AI agents | Accepted |
| [0010](0010-executable-rules-verify-tasks.md) | Executable rules, one verify command, playbooks, task queue | Accepted |
| [0011](0011-admin-storage-format.md) | Admin panel: JSON files on disk as storage format | Superseded by 0016 |
| [0012](0012-admin-ui-technology.md) | Admin panel: Freemarker + HTMX web UI | Accepted |
| [0013](0013-admin-preview.md) | Admin panel: draft preview via in-memory screen + QR code | Accepted |
| [0014](0014-admin-change-history.md) | Admin panel: append-only JSON snapshot log for change history | Superseded by 0016 (rollback semantics kept) |
| [0015](0015-screen-model-is-the-product.md) | The serializable screen model (`ScreenDefinition`) is the product | Accepted |
| [0016](0016-admin-storage-sqlite.md) | Admin panel storage: SQLite, accounts, two deployment modes | Accepted |
| [0017](0017-apps-in-own-repositories.md) | Apps live in their own repositories, built without framework code changes | Accepted (distribution: 0018) |
| [0018](0018-distribution-like-immich.md) | Distribution like Immich: release assets + GHCR image + Maven Central SDK | Accepted; Maven Central part superseded by 0019 |
| [0019](0019-self-hosted-maven-reposilite.md) | Android artifacts on a self-hosted Maven repository (Reposilite on the VPS) | Accepted |
| [0020](0020-component-tree-and-modifiers.md) | Component tree (column/row/box) and a fixed box-model modifier | Accepted |

## Template

```markdown
# NNNN — Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
What forces the decision; facts verified (with versions).

## Decision
What we do.

## Consequences
What gets easier/harder; rules agents must follow.

## How to verify it still holds
Command, test or check that proves the decision is still implemented / still necessary.
```
