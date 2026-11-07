package io.composecast.demo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

    // 10.0.2.2 is the Android emulator's alias for the host machine's localhost.
    // Change SERVER_URL for a real device or remote server.
    private val client = ComposeCastClient(
        ComposeCastConfig(
            serverUrl = SERVER_URL,
            logEnabled = true,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

    companion object {
        const val SERVER_URL = "http://10.0.2.2:8080"
    }
}

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
                title = {
                    val title = screenState?.getOrNull()?.title ?: screenId
                    Text(title)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                            "Cannot reach server.\nCheck SERVER_URL in MainActivity.\n\n${error?.message}"
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
