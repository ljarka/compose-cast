package io.composecast.android

import android.util.Log
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec

/**
 * Renders a [ScreenDocument] with the Remote Compose player.
 *
 * Host actions from the document are decoded with [ActionCodec] and delivered as typed [Action]s.
 * Unknown or malformed actions (e.g. a newer server) are logged and ignored.
 */
@Composable
fun ComposeCastScreen(screen: ScreenDocument, modifier: Modifier = Modifier, onAction: (Action) -> Unit = {}) {
    val currentOnAction by rememberUpdatedState(onAction)
    AndroidView(
        modifier = modifier,
        factory = { context -> RemoteComposePlayer(context) },
        update = { player ->
            // update runs on every recomposition; only (re)load when the document changes.
            if (player.tag !== screen.document) {
                player.tag = screen.document
                player.setDocument(screen.document)
                player.document.document.clearActionCallbacks()
                player.document.document.addActionCallback { name, _ ->
                    val action = ActionCodec.decode(name)
                    if (action !=
                        null
                    ) {
                        currentOnAction(action)
                    } else {
                        Log.w("ComposeCast", "Ignoring unknown action '$name'")
                    }
                }
            }
        },
    )
}
