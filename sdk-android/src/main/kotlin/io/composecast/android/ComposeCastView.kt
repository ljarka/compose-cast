package io.composecast.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.composecast.protocol.Action
import kotlinx.coroutines.CancellationException

/**
 * One server-driven screen, fetched and played (T-059): shows [loading] while [client] fetches
 * [screenId] with [params], then the screen, or [error] with a retry when it fails. A live screen
 * ([ScreenDocument.liveEveryMs]) is kept current over a WebSocket while the app is started.
 *
 * For several screens with a back stack use [ComposeCastFlow]; for full control
 * [ComposeCastClient.getScreen] + [ComposeCastScreen].
 *
 * @param client fetches the screen (one client per server, see [rememberComposeCastClient])
 * @param screenId the screen to show; changing it fetches the new screen
 * @param modifier size of the screen and of the loading/error slots (centered in it)
 * @param params query parameters of the screen (e.g. from [Action.Navigate.params])
 * @param reloadKey change it (e.g. a counter) to fetch the screen again
 * @param darkTheme which colors of themed documents and default slots to use
 * @param onAction host actions of the screen, e.g. [Action.Navigate]; nothing is handled here
 * @param onStateChanged called with every new state (e.g. to show [ComposeCastUiState.Ready]'s title)
 * @param loading shown while fetching; default [ComposeCastDefaults.Loading]
 * @param error shown when fetching failed, with a retry callback; default [ComposeCastDefaults.Error]
 */
@Composable
public fun ComposeCastView(
    client: ComposeCastClient,
    screenId: String,
    modifier: Modifier = Modifier,
    params: Map<String, String> = emptyMap(),
    reloadKey: Any = 0,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onAction: (Action) -> Unit = {},
    onStateChanged: ((ComposeCastUiState) -> Unit)? = null,
    loading: @Composable () -> Unit = { ComposeCastDefaults.Loading(darkTheme = darkTheme) },
    error: @Composable (error: ComposeCastError, retry: () -> Unit) -> Unit = { failure, retry ->
        ComposeCastDefaults.Error(failure, retry, darkTheme = darkTheme)
    },
) {
    var retries by remember { mutableIntStateOf(0) }
    val state by produceState<ComposeCastUiState>(
        ComposeCastUiState.Loading,
        client,
        screenId,
        params,
        reloadKey,
        retries,
    ) {
        value = ComposeCastUiState.Loading
        value = try {
            ComposeCastUiState.Ready(client.getScreen(screenId, params))
        } catch (e: CancellationException) {
            throw e // another screen was requested: not an error
        } catch (e: Exception) {
            ComposeCastUiState.Error(e.toComposeCastError())
        }
    }
    val currentOnStateChanged by rememberUpdatedState(onStateChanged)
    LaunchedEffect(state) { currentOnStateChanged?.invoke(state) }

    Box(modifier, contentAlignment = Alignment.Center) {
        when (val shown = state) {
            ComposeCastUiState.Loading -> loading()
            is ComposeCastUiState.Error -> error(shown.error) { retries++ }
            is ComposeCastUiState.Ready -> {
                val screen = shown.screen
                // Right after screenId changes, `screen` is still the previous document for a frame:
                // connect only when the document belongs to the requested screen.
                val live = remember(client, screen, screenId, params) {
                    if (screen.liveEveryMs != null &&
                        screen.id == screenId
                    ) {
                        client.liveScreen(screenId, params)
                    } else {
                        null
                    }
                }
                ComposeCastScreen(
                    screen = screen,
                    modifier = Modifier.fillMaxSize(),
                    live = live,
                    darkTheme = darkTheme,
                    onAction = onAction,
                )
            }
        }
    }
}
