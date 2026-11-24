package io.composecast.server.sdk

import io.composecast.server.sdk.render.RemoteComposeRenderer

/** One message of a live screen's stream (T-047, ADR 0029): a Remote Compose document. */
class LiveFrame(
    /** Full document (`setDocument`) or a data-update document; the player tells them apart by header. */
    val document: ByteArray,
    /** True when the structure changed (or first frame): the app replaces the whole screen. */
    val full: Boolean,
)

/**
 * The frames of one live screen for one viewer: call [next] every `live.everyMs`. The first frame
 * and every structure change send the full document; a change of data only sends a data-update
 * document; no change sends nothing. [registry] is asked on every call, so a publish or a data-dir
 * reload (a new registry) reaches open connections.
 */
class LiveFrames(
    private val id: String,
    private val context: RenderContext,
    private val registry: () -> ScreenRegistry?,
) {
    private var last: RemoteScreen? = null

    /** The screen's `live.everyMs` as of the last [next]; null before the first call or for a non-live screen. */
    val everyMs: Int? get() = last?.definition?.live?.everyMs

    /**
     * The next frame, or null when nothing changed. Blocks while data sources are fetched.
     * @throws NoSuchElementException when the screen no longer exists
     * @throws ScreenParameterException, DataSourceException, UnsupportedClientVersionException as [ScreenRegistry.render]
     */
    fun next(): LiveFrame? {
        val registry = registry() ?: throw NoSuchElementException("Screen '$id' not found")
        val screen = registry.render(id, context) ?: throw NoSuchElementException("Screen '$id' not found")
        val previous = last
        last = screen
        return when {
            previous == null -> LiveFrame(screen.document, full = true)
            previous.sameBytes(screen) -> null
            previous.structure != screen.structure -> LiveFrame(screen.document, full = true)
            else -> LiveFrame(registry.dataUpdate(screen, context), full = false)
        }
    }
}

/** Inspecting Remote Compose documents without RC types (debug endpoints, tests). */
object Documents {
    /** The decoded operation tree, as `/screens/{id}/debug` shows it. */
    fun describe(document: ByteArray): String = RemoteComposeRenderer.describe(document)

    /** What a data update cannot change; equal for a full document and its data updates (ADR 0029). */
    fun structure(document: ByteArray): String = RemoteComposeRenderer.structure(document)
}
