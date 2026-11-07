# 0004 — Units: DP density behavior + sp font expressions

- Status: Accepted (revised for alpha23)
- Date: 2026-11-07

## Context
Without a density header the player mixes px and dp and everything renders tiny. RC semantics
differ per version — verified in sources and with screenshot tests:

| | alpha20 | alpha23 |
|---|---|---|
| padding, border, clip radius in DP mode | × density | × density |
| width/height constant in DP mode | **not** scaled | × density |
| width/height expression in DP mode | **not** scaled | × density |
| font size constant in DP mode | not scaled | × density (no user font scale) |
| font size expression | not scaled | not scaled |
| `height(RcFloat)` writes expression first | **no (bug → 0)** | **no (bug → 0)** |

## Decision
- Header `DOC_DENSITY_BEHAVIOR = DENSITY_BEHAVIOR_DP`; all sizes are written in dp.
- Fonts: expression `FONT_SIZE * sp / 14` (player sets `FONT_SIZE` = 14sp in px with the user's
  font scale), so accessibility font scaling works. Helper `sp()`.
- Image height: device-side expression in dp, `min((windowWidth/density - 2*padding) * ratio,
  naturalHeight)`, wrapped in `written(...)` because of the `height(RcFloat)` bug.

## Consequences
Every RC upgrade must re-check this table. Using the alpha20 code on alpha23 changed 42% of the
`main` screenshot (double scaling) — the screenshot tests catch it.

## How to verify it still holds
`./gradlew :compat-tests:verifyRoborazziDebug`; `sdk-server` test "sizes are device-side
expressions, never collapsed to zero".
