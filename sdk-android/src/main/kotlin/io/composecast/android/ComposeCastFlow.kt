package io.composecast.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.composecast.protocol.Action

/**
 * Server-driven screens with a back stack (T-059): shows [navigator]'s current screen with
 * [ComposeCastView] and handles the screens' actions.
 *
 * Every action goes to [onAction] first; return `true` to handle it yourself (e.g. open a native
 * screen, log a [Action.Track]). Otherwise the flow handles it: [Action.Navigate] pushes the screen,
 * [Action.Reload] fetches the current one again, [Action.OpenUrl] calls [onOpenUrl]. Other actions
 * ([Action.Track], [Action.Custom]) do nothing unless [onAction] handles them; [Action.Toggle],
 * [Action.Show] and [Action.Hide] run on the device inside the document and are only reported.
 *
 * @param client fetches the screens (one client per server, see [rememberComposeCastClient])
 * @param navigator the back stack, see [rememberComposeCastNavigator]; an empty one shows [loading]
 * @param modifier size of the screens and slots
 * @param handleSystemBack system Back pops the back stack while [ComposeCastNavigator.canGoBack]
 *   (at the root, Back goes to the activity as usual)
 * @param darkTheme which colors of themed documents and default slots to use
 * @param onAction sees every action first; `true` = handled, the flow does nothing
 * @param onOpenUrl opens an [Action.OpenUrl]; by default an `ACTION_VIEW` intent (ignored, and
 *   logged, when no app can open it)
 * @param onStateChanged state of the current screen, e.g. its title for an app bar
 * @param loading shown while fetching; default [ComposeCastDefaults.Loading]
 * @param error shown when fetching failed, with a retry callback; default [ComposeCastDefaults.Error]
 */
@Composable
public fun ComposeCastFlow(
    client: ComposeCastClient,
    navigator: ComposeCastNavigator,
    modifier: Modifier = Modifier,
    handleSystemBack: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onAction: (Action) -> Boolean = { false },
    onOpenUrl: (String) -> Unit = defaultOpenUrl(LocalContext.current),
    onStateChanged: ((ComposeCastUiState) -> Unit)? = null,
    loading: @Composable () -> Unit = { ComposeCastDefaults.Loading(darkTheme = darkTheme) },
    error: @Composable (error: ComposeCastError, retry: () -> Unit) -> Unit = { failure, retry ->
        ComposeCastDefaults.Error(failure, retry, darkTheme = darkTheme)
    },
) {
    BackHandler(enabled = handleSystemBack && navigator.canGoBack) { navigator.pop() }
    val entry = navigator.current
    if (entry == null) {
        Box(modifier, contentAlignment = Alignment.Center) { loading() }
        return
    }
    // One composition per back stack position: a pushed screen (even the same id) starts fresh.
    key(navigator.backStack.size, entry) {
        ComposeCastView(
            client = client,
            screenId = entry.screen,
            modifier = modifier,
            params = entry.params,
            reloadKey = navigator.reloadCount,
            darkTheme = darkTheme,
            onAction = { action ->
                if (!onAction(action)) {
                    when (action) {
                        is Action.Navigate -> navigator.navigate(action)
                        Action.Reload -> navigator.reload()
                        is Action.OpenUrl -> onOpenUrl(action.url)
                        else -> Unit
                    }
                }
            },
            onStateChanged = onStateChanged,
            loading = loading,
            error = error,
        )
    }
}

/** Opens [url] with `ACTION_VIEW`; when no app can open it, logs and does nothing (no crash). */
private fun defaultOpenUrl(context: Context): (String) -> Unit = { url ->
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Log.w("ComposeCast", "No app can open $url")
    }
}
