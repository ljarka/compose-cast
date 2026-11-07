package io.composecast.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.composecast.android.ComposeCastClient
import io.composecast.android.ComposeCastConfig
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ScreenDocument
import io.composecast.protocol.Actions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 10.0.2.2 is the Android emulator's alias for the host machine's localhost.
    // Change SERVER_URL for a real device or remote server.
    private val client = ComposeCastClient(
        ComposeCastConfig(
            serverUrl = SERVER_URL,
            logEnabled = true,
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ComposeCastApp(
                    client = client,
                    onOpenUrl = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

// False positive: the producer below does assign `value`. The detector shipped with
// compose-runtime 1.7.5 misreads it under Kotlin 2.2 (also with `this.value`).
@SuppressLint("ProduceStateDoesNotAssignValue")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeCastApp(
    client: ComposeCastClient,
    onOpenUrl: (String) -> Unit,
) {
    // Back stack of screen ids; survives rotation. System back pops it.
    var backStack by rememberSaveable { mutableStateOf(listOf("main")) }
    val screenId = backStack.last()
    BackHandler(enabled = backStack.size > 1) { backStack = backStack.dropLast(1) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val screenState by produceState<Result<ScreenDocument>?>(initialValue = null, key1 = screenId) {
        value = null
        value = runCatching { client.getScreen(screenId) }
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

                screenState!!.isFailure -> Text(
                    text = "Cannot reach server.\nCheck SERVER_URL in MainActivity.\n\n${screenState!!.exceptionOrNull()?.message}",
                    modifier = Modifier.padding(16.dp),
                )

                else -> ComposeCastScreen(
                    screen = screenState!!.getOrThrow(),
                    modifier = Modifier.fillMaxSize(),
                    onAction = { action, payload ->
                        when (action) {
                            Actions.NAVIGATE -> backStack = backStack + payload
                            Actions.OPEN_URL -> onOpenUrl(payload)
                            Actions.TRACK -> { /* analytics hook */ }
                            else -> scope.launch {
                                snackbar.showSnackbar("Action: $action → $payload")
                            }
                        }
                    },
                )
            }
        }
    }
}
