package io.composecast.shell

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.composecast.android.ComposeCastClient
import io.composecast.android.ComposeCastConfig
import io.composecast.android.ComposeCastHttpException
import io.composecast.android.ComposeCastScreen
import io.composecast.android.ComposeCastUpgradeRequiredException
import io.composecast.android.ScreenDocument
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import kotlinx.coroutines.launch

/**
 * The whole app of a compose-cast Android app: fetches the server's screens and plays them, with a
 * back stack, loading/error states and action routing. Configured only through resources that the
 * `io.composecast.app` Gradle plugin sets from `compose-cast.properties` (T-011, T-024):
 * `composecast_server_url`, `composecast_frame_primary`, `composecast_frame_background`, `app_name`,
 * `composecast_preview` (the Preview app: server chosen at runtime, [PreviewLink]).
 */
class ComposeCastActivity : ComponentActivity() {

    private val framePrimary by lazy { Color(getColor(R.color.composecast_frame_primary)) }
    private val frameBackground by lazy { Color(getColor(R.color.composecast_frame_background)) }
    private val preview by lazy { resources.getBoolean(R.bool.composecast_preview) }
    private val previewStore by lazy { PreviewServerStore(this) }

    /** The server: `app.serverUrl`, or in the Preview app the linked/typed one (null = not chosen yet). */
    private var serverUrl by mutableStateOf<String?>(null)
    private var lastServerUrl by mutableStateOf<String?>(null)

    /** Preview app (T-045 B2): a linked screen to open on top (a draft preview); [OpenRequest.seq] makes each link count. */
    private var openRequest by mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverUrl = if (preview) {
            // After a rotation the intent is the same link again: its screen is already on the back stack.
            val link = PreviewLink.parse(intent.viewData())
            if (savedInstanceState == null) link?.screen?.let { openRequest = OpenRequest(it, 0) }
            link?.serverUrl?.also { previewStore.serverUrl = it } ?: previewStore.serverUrl
        } else {
            getString(R.string.composecast_server_url)
        }
        lastServerUrl = serverUrl
        // Status bar sits on the top app bar (frame primary color): light or dark icons to match.
        val statusBar = framePrimary.let {
            if (it.luminance() > 0.5f) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
        }
        enableEdgeToEdge(statusBarStyle = statusBar)
        setContent {
            MaterialTheme(colorScheme = frameColorScheme(framePrimary, frameBackground)) {
                val url = serverUrl
                if (url == null) {
                    ConnectScreen(initialAddress = lastServerUrl.orEmpty(), onConnect = ::connect)
                } else {
                    // A new server starts over: new client, empty back stack.
                    key(url) {
                        val client = remember {
                            ComposeCastClient(ComposeCastConfig(serverUrl = url, logEnabled = true))
                        }
                        DisposableEffect(client) { onDispose { client.close() } }
                        ComposeCastApp(
                            client = client,
                            serverUrl = url,
                            unreachableHint = if (preview) {
                                "Check the server address (Change server)."
                            } else {
                                "Check app.serverUrl in compose-cast.properties."
                            },
                            onChangeServer = if (preview) ({ connect(null) }) else null,
                            openRequest = openRequest,
                            onOpenUrl = { link ->
                                // false = no app can open it (would otherwise crash with ActivityNotFoundException).
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                    true
                                } catch (e: ActivityNotFoundException) {
                                    false
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /** A connect link while running (the activity is `singleTop`): switch to that server (and screen). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!preview) return
        val link = PreviewLink.parse(intent.viewData()) ?: return
        connect(link.serverUrl)
        link.screen?.let { openRequest = OpenRequest(it, (openRequest?.seq ?: 0) + 1) }
    }

    /** Preview app: remember [url] (null = forget it and show the connect screen). */
    private fun connect(url: String?) {
        if (url != null) lastServerUrl = url
        previewStore.serverUrl = url
        serverUrl = url
    }

    private fun Intent?.viewData(): String? = this?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
}

/** Open [screen] on top of the back stack; a new [seq] = a new request (the same draft scanned twice). */
private data class OpenRequest(val screen: String, val seq: Int)

/**
 * Colors of the native frame around the server documents (top app bar, loading/error states) from
 * `app.frame.*`. Content colors are the server's brand, rendered into the documents.
 */
private fun frameColorScheme(primary: Color, background: Color): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = contentColorFor(primary),
    background = background,
    onBackground = contentColorFor(background),
    surface = background,
    onSurface = contentColorFor(background),
)

/** Black or white, whichever reads better on [color]. */
private fun contentColorFor(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeCastApp(
    client: ComposeCastClient,
    serverUrl: String,
    unreachableHint: String,
    onChangeServer: (() -> Unit)?,
    openRequest: OpenRequest?,
    onOpenUrl: (String) -> Boolean,
) {
    // Back stack of (screen, params) entries; survives rotation. System back pops it.
    var backStack by rememberSaveable(stateSaver = BackStackSaver) {
        mutableStateOf(listOf(Action.Navigate("main")))
    }
    val entry = backStack.last()
    val screenId = entry.screen
    BackHandler(enabled = backStack.size > 1) { backStack = backStack.dropLast(1) }
    // A linked screen (draft preview) opens on top; rememberSaveable: not again after a rotation.
    var handledRequest by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        if (request.seq == handledRequest) return@LaunchedEffect
        handledRequest = request.seq
        backStack = backStack + Action.Navigate(request.screen)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var reloadKey by remember { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings && onChangeServer != null) {
        PreviewSettingsScreen(
            serverUrl = serverUrl,
            onBack = { showSettings = false },
            onChangeServer = onChangeServer,
        )
        return
    }
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
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    val title = screenState?.getOrNull()?.title ?: stringResource(R.string.app_name)
                    Text(title)
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh")
                    }
                    if (onChangeServer != null) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                        }
                    }
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
                        text = when (error) {
                            is ComposeCastUpgradeRequiredException ->
                                "Please update the app to continue."
                            is ComposeCastHttpException ->
                                // The server answered (e.g. 404 unknown screen): not a connectivity problem.
                                "Server error ${error.status} for screen '$screenId'."
                            else ->
                                "Cannot reach $serverUrl.\n$unreachableHint\n\n${error?.message}"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSettingsScreen(serverUrl: String, onBack: () -> Unit, onChangeServer: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                title = { Text("Settings") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Connected server", style = MaterialTheme.typography.labelLarge)
            Text(serverUrl, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onChangeServer) { Text("Change server") }
        }
    }
}
