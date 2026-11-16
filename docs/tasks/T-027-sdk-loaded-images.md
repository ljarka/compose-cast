# T-027 — Images loaded by the Android SDK (named bitmaps) for lists

Status: queued (after T-025) · Roadmap: Etap 5 · Depends on: — (blocks the TV shows milestone)

## Goal

Lists with many or large images (posters, galleries) must not make documents megabytes in size.
The server references these images by name, and the SDK downloads them asynchronously and hands
them to the player as named bitmaps.

## Why

ADR 0003 embeds every image in the document because RC alpha23 URL images do not work
(`Limits.ENABLE_IMAGE_URLS = false`, 1x1 size check, synchronous fetch). That is fine for a hero
image but not for 20 posters: a slow first paint and large ETag payloads.

## Design (verify each RC fact in the sources jar first: AGENTS.md rule 3)

1. Research in alpha23: how a document declares a named or replaceable bitmap with a declared
   size, and the player/state API to supply or replace it at runtime (look for named
   bitmap/`setNamedBitmap`/`updateBitmap` in `RemoteComposeState` and the player sources). Record
   the findings in `docs/remote-compose.md`. **If there is no such API, stop:** write an ADR
   proposing alternatives (thumbnails embedded and resized on the server, pagination) and ask the
   owner.
2. Model: `image.loading: "embed" | "device"` (default `embed`, so current behavior stays). With
   `device`, `width`/`height` (or `aspectRatio`) are required so the layout does not jump.
3. Server: emit a placeholder bitmap of the declared size, named `device:<url>`, and list the URLs
   in a response header or a document metadata field.
4. `sdk-android`: after the document loads, fetch the URLs in parallel (limited concurrency, memory
   LRU cache, decode at the declared size) and supply them to the player. A failure keeps the
   placeholder.
5. Alternative cheap win: server-side resizing of embedded images to the declared size times the
   device density (needs the density in the request). Evaluate it in the same task.

## Acceptance criteria

- A demo screen `gallery-device` with 20 images: the document is under 50 KB, images appear on the
  emulator, and scrolling stays smooth.
- Screenshot test with a fake loader. `./scripts/verify.sh` is green.

## Docs to update

ADR 0003 (new ADR if the decision changes), `docs/remote-compose.md`, `protocol/AGENTS.md`,
`sdk-android/AGENTS.md`, README (Etap 5 image box), history.
