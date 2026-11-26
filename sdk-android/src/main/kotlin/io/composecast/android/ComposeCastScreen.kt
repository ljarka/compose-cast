package io.composecast.android

import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import kotlinx.coroutines.flow.Flow

/**
 * Renders a [ScreenDocument] with the Remote Compose player.
 *
 * Host actions from the document are decoded with [ActionCodec] and delivered as typed [Action]s.
 * Unknown or malformed actions (e.g. a newer server) are logged and ignored.
 *
 * [live] keeps a live screen current (T-047, ADR 0029): pass [ComposeCastClient.liveScreen] when
 * [ScreenDocument.liveEveryMs] is set. It is collected only while the app is at least started (the
 * socket closes in the background and reopens on return); data updates replace values in place
 * (state, scroll and animations stay), full documents replace the screen.
 */
@Composable
fun ComposeCastScreen(
    screen: ScreenDocument,
    modifier: Modifier = Modifier,
    live: Flow<ScreenDocument>? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onAction: (Action) -> Unit = {},
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val rcTheme = if (darkTheme) RemoteComposePlayer.THEME_DARK else RemoteComposePlayer.THEME_LIGHT
    var player by remember { mutableStateOf<RemoteComposePlayer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RemoteComposePlayer(context).also {
                it.setTheme(rcTheme)
                player = it
            }
        },
        update = { player ->
            player.setTheme(rcTheme)
            // update runs on every recomposition; only (re)load when the document changes.
            if (player.tag !== screen.document) {
                player.tag = screen.document
                player.show(screen.document) { currentOnAction(it) }
                player.setTheme(rcTheme)
            }
        },
    )
    if (live != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // The key's value, not the state: the effect starts after AndroidView's factory has set
        // `player`, so reading the state would connect once more before the key change restarts it.
        val keyedPlayer = player
        LaunchedEffect(live, keyedPlayer, screen) {
            val target = keyedPlayer ?: return@LaunchedEffect
            var shown = screen.contentHash
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                live.collect { frame ->
                    when {
                        frame.isDataUpdate -> target.updateDocument(frame.document)
                        // The first frame after connecting usually equals what GET returned.
                        frame.contentHash == shown -> Unit
                        else -> {
                            target.show(frame.document) { currentOnAction(it) }
                            shown = frame.contentHash
                        }
                    }
                }
            }
        }
    }
}

/** Plays [document] and routes its host actions to [onAction]. */
private fun RemoteComposePlayer.show(document: RemoteDocument, onAction: (Action) -> Unit) {
    setDocument(document)
    this.document.document.clearActionCallbacks()
    this.document.document.addActionCallback { name, _ ->
        val action = ActionCodec.decode(name)
        if (action != null) onAction(action) else Log.w("ComposeCast", "Ignoring unknown action '$name'")
    }
}
