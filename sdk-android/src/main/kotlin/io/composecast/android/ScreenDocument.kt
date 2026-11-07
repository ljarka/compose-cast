package io.composecast.android

import androidx.compose.remote.player.core.RemoteDocument

/**
 * A screen fetched from the server: a decoded Remote Compose document plus its metadata.
 * Render it with [ComposeCastScreen].
 */
class ScreenDocument internal constructor(
    val id: String,
    val title: String,
    /** Size of the Remote Compose document as received, in bytes. */
    val sizeBytes: Int,
    internal val document: RemoteDocument,
) {
    companion object {
        /**
         * Decodes a Remote Compose document obtained some other way than [ComposeCastClient]
         * (e.g. bundled offline, cached, or generated in tests). Call off the main thread.
         */
        fun fromBytes(id: String, title: String, bytes: ByteArray): ScreenDocument =
            ScreenDocument(id, title, bytes.size, RemoteDocument(bytes))
    }
}
