package io.composecast.android

import androidx.compose.remote.player.core.RemoteDocument

/**
 * A screen fetched from the server: a decoded Remote Compose document plus its metadata.
 * Render it with [ComposeCastScreen].
 */
public class ScreenDocument internal constructor(
    public val id: String,
    public val title: String,
    /** Size of the Remote Compose document as received, in bytes. */
    public val sizeBytes: Int,
    private val rawDocument: RemoteDocument?,
    /**
     * For a live screen (T-047, ADR 0029) the server's update interval in ms: pass
     * [ComposeCastClient.liveScreen] to [ComposeCastScreen] to keep it current. Null otherwise.
     */
    public val liveEveryMs: Int? = null,
    /** Identifies the document's bytes: a live stream's first frame equal to it is skipped. */
    internal val contentHash: Int = 0,
) {
    internal val document: RemoteDocument
        get() = checkNotNull(rawDocument) { "ScreenDocument '$id' has no decoded RemoteDocument" }

    /** A data-update document (live screens): replaces only the values of the shown screen. */
    internal val isDataUpdate: Boolean get() = document.isUpdateDoc

    public companion object {
        /**
         * Decodes a Remote Compose document obtained some other way than [ComposeCastClient]
         * (e.g. bundled offline, cached, or generated in tests). Call off the main thread.
         */
        public fun fromBytes(id: String, title: String, bytes: ByteArray, liveEveryMs: Int? = null): ScreenDocument =
            ScreenDocument(id, title, bytes.size, RemoteDocument(bytes), liveEveryMs, bytes.contentHashCode())
    }
}
