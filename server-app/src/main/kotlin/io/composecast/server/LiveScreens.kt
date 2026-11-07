package io.composecast.server

import io.composecast.server.sdk.ScreenRegistry
import java.util.concurrent.atomic.AtomicReference

/**
 * The registry currently served. Routing reads [current] per request, so a new registry (admin
 * publish, T-007; data-dir reload) can be swapped in atomically while the server runs.
 */
class LiveScreens(initial: ScreenRegistry) {
    private val ref = AtomicReference(initial)

    val current: ScreenRegistry get() = ref.get()

    /** Atomically serves [registry] from the next request on. Build (and so validate) it first. */
    fun replace(registry: ScreenRegistry) = ref.set(registry)
}
