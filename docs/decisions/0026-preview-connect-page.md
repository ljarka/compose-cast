# 0026 — The Preview app connects through the server's `/connect` page

- Status: Accepted
- Date: 2026-11-23
- Context of: [0024](0024-generic-preview-app.md) (its open question "QR library vs. system camera + deep link", T-045 B1)

## Context

The admin panel shows a QR code that must get the server's address into the compose-cast Preview
app. Options: a QR scanner inside the app (camera permission, a scanning library), or a QR with a
custom-scheme link `composecast://connect?url=…` for the phone's own camera. Phone cameras reliably
open `https://` links, but many show a custom-scheme QR only as text.

## Decision

- The QR code holds an ordinary link to the server: `https://<server>/connect` (B2 adds the draft).
  Any camera opens it in the browser.
- `GET /connect` (`server-app`, public, no data) is a small page whose button is an Android
  `intent://connect?url=…#Intent;scheme=composecast;package=io.composecast.preview;S.browser_fallback_url=<latest compose-cast-preview.apk>;end`
  link: it opens the Preview app, or downloads the APK when it is not installed. A plain
  `composecast://connect?url=…` link and the address to type are shown as fallbacks.
- The app handles `composecast://connect?url=<encoded http(s) URL>` on an `activity-alias` enabled
  only by `@bool/composecast_preview` (`app.preview=true`), so branded apps never accept another
  server. It can also type the address. No camera permission, no scanning library.

## Consequences

- The server builds its own address from `X-Forwarded-Proto` + `Host` (validated characters).
- Any page can switch the Preview app to another server with a link; acceptable for a preview tool
  (the app shows what that server sends, nothing else is exposed). Never for branded apps.
- Works with Chrome and Samsung Internet (`intent://`); other browsers get the fallback link.

## How to verify it still holds

`RoutingTest` (`connect page …`), `PreviewLinkTest`; on a device: `maestro test -e
SERVER=http://10.0.2.2:<port> app-preview/maestro/connect.yaml`;
`adb shell cmd package query-activities -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "composecast://connect?url=x"`
lists only `io.composecast.preview`.
