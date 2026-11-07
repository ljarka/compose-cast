# 0006 — Screenshot tests are the golden compatibility tests

- Status: Accepted
- Date: 2026-11-07

## Context
Every rendering bug so far (units, disappearing heights, image failures) was invisible to unit
tests and only showed on a device. RC behavior changes between alphas.

## Decision
`compat-tests` renders the server's demo screens through the real Android player under Robolectric
+ Roborazzi (no emulator, no network) and compares with PNGs in
`compat-tests/src/test/screenshots/`, which are also the README images. CI runs
`:compat-tests:verifyRoborazziDebug`. Tolerance 0.02% of pixels (macOS goldens vs Linux CI; two
changed words on a button ≈ 0.14%).

## Consequences
- Any visual change: verify; if intentional, record, **look at the PNGs**, commit them.
- The Material3 TopAppBar frame differs between Robolectric and devices; check whether RC content
  changed before re-recording.
- Every new component or demo screen gets a screenshot test.

## How to verify it still holds
CI step "Verify screenshots"; mutate a demo text and see `verifyRoborazziDebug` fail.
