package io.composecast.android

import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders a [ScreenDocument] with the Remote Compose player.
 *
 * Host actions from the document (e.g. a button with action `navigate:onboarding`) are split
 * on the first `:` and delivered as `onAction("navigate", "onboarding")`.
 */
@Composable
fun ComposeCastScreen(
    screen: ScreenDocument,
    modifier: Modifier = Modifier,
    onAction: (action: String, payload: String) -> Unit = { _, _ -> },
) {
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
                    val action = name.substringBefore(':')
                    val payload = name.substringAfter(':', missingDelimiterValue = "")
                    currentOnAction(action, payload)
                }
            }
        },
    )
}
