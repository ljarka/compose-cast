package io.composecast.server

import io.composecast.server.sdk.ScreenRegistry
import java.util.concurrent.atomic.AtomicReference

/**
 * The registry currently served. Routing reads [current] per request, so a new registry (admin
 * publish, T-007; data-dir reload) can be swapped in atomically while the server runs.
 */
class LiveScreens(
    initial: ScreenRegistry,
    val previews: PreviewScreens = PreviewScreens(),
    initialHome: String? = null,
) {
    private val ref = AtomicReference(initial)
    private val homeRef = AtomicReference(initialHome)

    val current: ScreenRegistry get() = ref.get()
    val homeScreen: String? get() = homeRef.get()

    /** The registry that serves screen [id]: a draft preview's (T-045 B2) or the live one. */
    fun registryFor(id: String): ScreenRegistry? = if (PreviewScreens.isPreview(id)) previews[id] else current

    /** Atomically serves [registry] from the next request on. Build (and so validate) it first. */
    fun replace(registry: ScreenRegistry) = ref.set(registry)

    /** Atomically switches which screen id is served for `main`. */
    fun replaceHome(screenId: String?) = homeRef.set(screenId)
}
