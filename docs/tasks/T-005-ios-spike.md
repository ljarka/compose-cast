# T-005 — iOS spike

Status: blocked: needs Xcode on the dev machine · Roadmap: Etap 3 · Depends on: — (T-002 helps for option b)

## Goal
Decide the iOS renderer with evidence ([ADR 0007](../decisions/0007-ios-strategy-spike.md)).

## Steps
1. **(a) Community RC engine** (`camaelon/remotecompose-experiments`, `players/cpp`, iOS SwiftUI
   viewer): build it, play `curl localhost:8080/screens/main > main.rc` and `onboarding`. Record:
   build effort, fidelity vs `compat-tests/src/test/screenshots/*.png`, text scaling, images, does a
   host action reach Swift code, operations unsupported (see its STATUS.md).
2. **(b) DivKit iOS**: minimal iOS app with DivKit; a throwaway server renderer `ScreenDefinition →
   DivKit JSON` for the demo screens (T-002 model); same measurements.
3. Write the report into ADR 0007's successor (new ADR, status Accepted) with screenshots in
   `docs/spikes/ios/`, and set the follow-up tasks (iOS shell, SDK).

## Acceptance criteria
Both options tried on the demo screens, side-by-side screenshots, a decision ADR.
