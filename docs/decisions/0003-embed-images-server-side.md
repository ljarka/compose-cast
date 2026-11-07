# 0003 — Embed images in documents on the server

- Status: Accepted
- Date: 2026-11-07

## Context
Verified in alpha20 and alpha23 sources: URL images in the player are disabled by default
(`Limits.ENABLE_IMAGE_URLS = false`), size-checked against the size declared in the document
(`CHECK_DATA_SIZE = true`) while the DSL's `remoteBitmapUrl` always declares 1x1, and fetched
synchronously during document load (main thread). Real photos therefore fail.

## Decision
The server fetches each image at render time (`ImageLoader`, default `HttpImageLoader`) and embeds
the original bytes with the real size, named `image:<url>`. A failed fetch renders the alt text and
logs a warning; the server keeps running. Tests inject a fake `ImageLoader` (no network).

## Consequences
- Documents grow with images (fine for a few hero images, not for long lists).
- Planned alternative for lists: the SDK loads images itself and supplies them as named bitmaps
  (roadmap). Revisit if RC fixes URL images.

## How to verify it still holds
`sdk-server` test "image is embedded with its real size, decodable without URL support".
Re-check `Limits.ENABLE_IMAGE_URLS` / `CHECK_DATA_SIZE` / `addBitmapUrl(url, 1, 1)` on RC upgrades.
