package io.composecast.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * A [ComposeCastClient] for [config] that lives as long as this composition: it is closed when the
 * composable leaves the composition, and replaced (the old one closed) when [config] changes.
 *
 * Good for a single screen or a small app. With several screens or a DI framework, create one
 * client per server outside Compose and [ComposeCastClient.close] it yourself: clients hold a
 * connection pool and an ETag cache, so sharing one is cheaper.
 *
 * Pass a stable [config] (create it once, not inline with a new [ComposeCastConfig.headers]
 * lambda on every recomposition): a config that is not equal to the previous one creates a new client.
 */
@Composable
public fun rememberComposeCastClient(config: ComposeCastConfig): ComposeCastClient {
    val client = remember(config) { ComposeCastClient(config) }
    DisposableEffect(client) { onDispose { client.close() } }
    return client
}
