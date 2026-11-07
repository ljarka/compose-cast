# 0013 — Admin panel: preview strategy

- Status: Accepted
- Date: 2026-11-07

## Context

After editing a screen in the admin panel, the user needs a preview before publishing. Options:

1. **TypeScript RC player** (`camaelon/remotecompose-experiments`) — runs in the browser; no
   device needed. Unofficial, unmaintained, and may lag behind the binary format. Requires a
   bundled JS build step, contradicting ADR 0012.
2. **Server-rendered PNG** — the server renders the RC document to a PNG (using Roborazzi or a
   headless Compose renderer) and serves it as an `<img>`. Clean; no browser JS complexity.
   Problem: no headless Compose renderer runs on a standard JVM/Linux server without an Android
   runtime.
3. **"Preview on device" via WebSocket push** — the server pushes the draft `.rc` document over
   a WebSocket to a connected mobile app; the app renders it natively. Requires a connected
   device but gives pixel-perfect, real-device rendering.
4. **Live reload URL** — the admin panel returns a URL (e.g.
   `http://server:8080/screens/__preview`) that serves the current draft; the developer opens
   this URL in the `app-template` by typing it, or scans a QR code.

Option 4 requires no WebSocket plumbing and no browser-side JS renderer. The draft is a
temporary RC document held in memory; it is replaced on each preview request and never persisted
unless the user publishes.

## Decision

**Draft preview via a temporary in-memory screen** served at `GET /screens/__preview`:

1. `POST /admin/api/screens/{id}/preview` compiles the draft JSON → RC bytes and stores them in
   a per-session in-memory cache (TTL 5 min).
2. Response body: the screen's id `__preview` and a QR code PNG (base64 data URI) pointing to
   `http://<server>:<port>/screens/__preview`.
3. The developer scans the QR code with `app-template` (or navigates to `__preview` manually) to
   see the screen on a real device.
4. The admin HTML page shows the QR code inline (`<img src="data:image/png;base64,..."/>`).

If a browser-side RC player matures and is stable enough, add it as a complementary option
without removing the QR flow.

## Consequences

- No browser-side JS renderer dependency; ADR 0012 (HTMX / no build step) holds.
- Preview requires a connected device or emulator — acceptable for developer users.
- `GET /screens/__preview` is never persisted; it disappears on server restart.
- Agents: `__preview` is a reserved screen id; `ScreenRegistry.get("__preview")` must return
  null for the persistent registry; the preview cache is a separate object in `admin-server`.

## How to verify it still holds

```bash
curl -u admin:$ADMIN_PASSWORD -X POST http://localhost:8080/admin/api/screens/main/preview \
  -H 'Content-Type: application/json' -d '{"components":[]}'
# response must contain {"previewUrl":"…/__preview","qrCode":"data:image/png;…"}
curl http://localhost:8080/screens/__preview | file -  # should be RC binary
```
