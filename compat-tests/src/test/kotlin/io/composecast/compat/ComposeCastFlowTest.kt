package io.composecast.compat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.composecast.android.ComposeCastClient
import io.composecast.android.ComposeCastConfig
import io.composecast.android.ComposeCastError
import io.composecast.android.ComposeCastFlow
import io.composecast.android.ComposeCastNavigator
import io.composecast.android.ComposeCastUiState
import io.composecast.protocol.Action
import io.composecast.protocol.Dimension
import io.composecast.server.LiveScreens
import io.composecast.server.module
import io.composecast.server.sdk.composeCast
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import io.composecast.protocol.Modifier as CastModifier

/**
 * T-059: the SDK's high-level [ComposeCastFlow] against a real server (Netty on a random port) and the
 * real client, played by the RC player under Robolectric: fetch, tap → navigate with params, host
 * interception, system Back, error slot + retry.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ComposeCastFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var client: ComposeCastClient
    private val liveSockets = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val openSockets = java.util.concurrent.atomic.AtomicInteger()

    /** The whole screen is one tappable box: a tap anywhere runs [action]. */
    private fun fullScreenTap(action: Action) =
        CastModifier(width = Dimension.Fill, height = Dimension.Fill, action = action)

    @Before
    fun start() {
        val registry = composeCast(imageLoader = GeneratedImage) {
            screen("main") {
                box(modifier = fullScreenTap(Action.Navigate("detail", mapOf("id" to "7")))) { text("Main") }
            }
            screen("dash") {
                live(everyMs = 100)
                box(modifier = fullScreenTap(Action.Navigate("main"))) { text("Live") }
            }
            screen("detail") {
                param("id")
                box(modifier = fullScreenTap(Action.OpenUrl("https://example.com/{{params.id}}"))) {
                    text("Detail {{params.id}}")
                }
            }
        }
        server = embeddedServer(Netty, port = 0) {
            install(
                createApplicationPlugin("RecordLiveSockets") {
                    onCall { call ->
                        if (call.request.path().endsWith("/live")) {
                            liveSockets += call.request.path()
                            openSockets.incrementAndGet()
                        }
                    }
                    on(ResponseSent) { call ->
                        if (call.request.path().endsWith("/live")) openSockets.decrementAndGet()
                    }
                },
            )
            module(LiveScreens(registry))
        }.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = ComposeCastClient(ComposeCastConfig(serverUrl = "http://127.0.0.1:$port"))
    }

    @After
    fun stop() {
        // Dispose the composition first and let the server finish its live sockets: a socket still
        // open at shutdown makes Netty log a RejectedExecutionException.
        compose.activityRule.scenario.close()
        val deadline = System.currentTimeMillis() + 5_000
        while (openSockets.get() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        client.close()
        server.stop(gracePeriodMillis = 100, timeoutMillis = 2_000)
    }

    private fun ComposeCastUiState?.readyId() = (this as? ComposeCastUiState.Ready)?.screen?.id

    /**
     * Network answers arrive on OkHttp threads and resume on the main looper, which Robolectric keeps
     * paused: run it while waiting (compose's waitUntil alone never sees the state change).
     */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.waitForIdle()
        }
    }

    /** The RC player lays components out while drawing: draw a frame, then tap the middle. */
    private fun tapScreen() {
        compose.onRoot().captureToImage()
        compose.onRoot().performTouchInput { click(center) }
    }

    @Test
    fun `navigates with params, lets the host intercept, and pops on Back`() {
        val navigator = ComposeCastNavigator(listOf(Action.Navigate("main")))
        var state: ComposeCastUiState? = null
        val intercepted = mutableListOf<Action>()
        compose.setContent {
            ComposeCastFlow(
                client = client,
                navigator = navigator,
                modifier = Modifier.fillMaxSize(),
                darkTheme = false,
                onAction = { action -> (action is Action.OpenUrl).also { if (it) intercepted += action } },
                onStateChanged = { state = it },
            )
        }
        waitFor { state.readyId() == "main" }

        tapScreen() // default handling: push the button's Navigate
        waitFor { state.readyId() == "detail" }
        assertEquals(Action.Navigate("detail", mapOf("id" to "7")), navigator.current)

        tapScreen() // intercepted: onAction returned true, so the default (open a browser) did not run
        compose.waitForIdle()
        assertEquals(listOf<Action>(Action.OpenUrl("https://example.com/7")), intercepted)
        assertEquals(2, navigator.backStack.size)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { state.readyId() == "main" }
        assertEquals(listOf(Action.Navigate("main")), navigator.backStack)
    }

    @Test
    fun `unknown screen shows the error slot and Retry fetches again`() {
        val navigator = ComposeCastNavigator(listOf(Action.Navigate("missing")))
        val states = mutableListOf<ComposeCastUiState>()
        compose.setContent {
            ComposeCastFlow(
                client = client,
                navigator = navigator,
                modifier = Modifier.fillMaxSize(),
                darkTheme = false,
                onStateChanged = { states += it },
            )
        }
        waitFor { states.lastOrNull() is ComposeCastUiState.Error }
        assertEquals(
            ComposeCastError.Http(404, "HTTP 404 for screen 'missing'"),
            (states.last() as ComposeCastUiState.Error).error,
        )
        compose.onNodeWithText("Server error 404.").assertExists()
        compose.onRoot().captureRoboImage(
            "src/test/screenshots/sdk-error-slot.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0002f),
            ),
        )

        val before = states.size
        compose.onNodeWithText("Retry").performClick()
        waitFor { states.size >= before + 2 && states.last() is ComposeCastUiState.Error }
        assertTrue(states.subList(before, states.size).first() is ComposeCastUiState.Loading)
    }

    @Test
    fun `a live screen opens its WebSocket, closes it when left, and reopens it after Back`() {
        val navigator = ComposeCastNavigator(listOf(Action.Navigate("dash")))
        var state: ComposeCastUiState? = null
        compose.setContent {
            ComposeCastFlow(client, navigator, Modifier.fillMaxSize(), darkTheme = false, onStateChanged = {
                state = it
            })
        }
        waitFor { state.readyId() == "dash" && liveSockets.size == 1 }
        assertEquals(100, (state as ComposeCastUiState.Ready).screen.liveEveryMs)

        tapScreen() // dash -> main (not live): no socket for main, and the dash one is not reopened
        waitFor { state.readyId() == "main" }
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals(listOf("/screens/dash/live"), liveSockets.toList())

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { state.readyId() == "dash" && liveSockets.size == 2 }
        assertEquals(listOf("/screens/dash/live", "/screens/dash/live"), liveSockets.toList())
    }
}
