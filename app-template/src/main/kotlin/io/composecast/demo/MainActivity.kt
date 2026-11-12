package io.composecast.demo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.composecast.android.ComposeCastClient
import io.composecast.android.ComposeCastConfig
import io.composecast.android.ComposeCastHttpException
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Server URL and frame colors come from compose-cast.properties (BuildConfig, T-011).
    private val client = ComposeCastClient(
        ComposeCastConfig(
            serverUrl = BuildConfig.SERVER_URL,
            logEnabled = true,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Status bar sits on the top app bar (frame primary color): light or dark icons to match.
        val statusBar = Color(BuildConfig.FRAME_PRIMARY_COLOR).let {
            if (it.luminance() > 0.5f) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
        }
        enableEdgeToEdge(statusBarStyle = statusBar)
        setContent {
            MaterialTheme(colorScheme = frameColorScheme()) {
                ComposeCastApp(
                    client = client,
                    onOpenUrl = { url ->
                        // false = no app can open it (would otherwise crash with ActivityNotFoundException).
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            true
                        } catch (e: ActivityNotFoundException) {
                            false
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}

/**
 * Colors of the native frame around the server documents (top app bar, loading/error states) from
 * `app.frame.*`. Content colors are the server's brand, rendered into the documents.
 */
private fun frameColorScheme(): ColorScheme {
    val primary = Color(BuildConfig.FRAME_PRIMARY_COLOR)
    val background = Color(BuildConfig.FRAME_BACKGROUND_COLOR)
    return lightColorScheme(
        primary = primary,
        onPrimary = contentColorFor(primary),
        background = background,
        onBackground = contentColorFor(background),
        surface = background,
        onSurface = contentColorFor(background),
    )
}

/** Black or white, whichever reads better on [color]. */
private fun contentColorFor(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeCastApp(client: ComposeCastClient, onOpenUrl: (String) -> Boolean) {
    // Back stack of (screen, params) entries; survives rotation. System back pops it.
    var backStack by rememberSaveable(stateSaver = BackStackSaver) {
        mutableStateOf(listOf(Action.Navigate("main")))
    }
    val entry = backStack.last()
    val screenId = entry.screen
    BackHandler(enabled = backStack.size > 1) { backStack = backStack.dropLast(1) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var reloadKey by remember { mutableIntStateOf(0) }
    val screenState by produceState<Result<ScreenDocument>?>(initialValue = null, key1 = entry, key2 = reloadKey) {
        value = null
        value = runCatching { client.getScreen(entry.screen, entry.params) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    val title = screenState?.getOrNull()?.title ?: screenId
                    Text(title)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                screenState == null -> CircularProgressIndicator()

                screenState!!.isFailure -> Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val error = screenState!!.exceptionOrNull()
                    Text(
                        text = if (error is ComposeCastHttpException) {
                            // The server answered (e.g. 404 unknown screen): not a connectivity problem.
                            "Server error ${error.status} for screen '$screenId'."
                        } else {
                            "Cannot reach ${BuildConfig.SERVER_URL}.\nCheck app.serverUrl in compose-cast.properties.\n\n${error?.message}"
                        },
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { reloadKey++ }) { Text("Retry") }
                }

                else -> ComposeCastScreen(
                    screen = screenState!!.getOrThrow(),
                    modifier = Modifier.fillMaxSize(),
                    onAction = { action ->
                        when (action) {
                            is Action.Navigate -> backStack = backStack + action
                            is Action.OpenUrl -> if (!onOpenUrl(action.url)) {
                                scope.launch { snackbar.showSnackbar("No app can open ${action.url}") }
                            }
                            Action.Reload -> reloadKey++
                            is Action.Track -> { /* analytics hook */ }
                            is Action.Custom -> scope.launch { snackbar.showSnackbar("Custom: ${action.payload}") }
                        }
                    },
                )
            }
        }
    }
}

/** Saves back stack entries as their action wire form ([ActionCodec]): Bundle-friendly strings. */
private val BackStackSaver = listSaver<List<Action.Navigate>, String>(
    save = { stack -> stack.map(ActionCodec::encode) },
    restore = { saved -> saved.mapNotNull { ActionCodec.decode(it) as? Action.Navigate } },
)
