package io.composecast.renderer

import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.composecast.protocol.Action
import io.composecast.server.sdk.composeCast
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The preview renderer service (T-048). A "test" because Robolectric only boots inside its test
 * runner: [serve] never returns. It is skipped in normal test runs; [main] starts it with
 * `composecast.renderer.serve=true`.
 *
 * - `GET /health` → 200 once the sandbox is up and can render.
 * - `POST /render` with an RC document as the body → `image/png` (phone: Pixel 7, 411×914 dp,
 *   2.625x); 400 if the body is not an RC document.
 *
 * HTTP threads only queue work: Robolectric and Compose run on this test's main thread.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class RenderServer {
    @get:Rule
    val compose = createComposeRule()

    private class Job(
        val document: ByteArray,
        val dark: Boolean = false,
        val result: CompletableFuture<ByteArray> = CompletableFuture(),
    )

    @Test
    fun serve() {
        assumeTrue("starts only from main()", System.getProperty(SERVE) == "true")
        val renderer = Renderer(compose)
        val jobs = LinkedBlockingQueue<Job>()
        val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
        val server = HttpServer.create(InetSocketAddress(port), 0).apply {
            executor = Executors.newFixedThreadPool(4)
            createContext("/health") { it.respond(200, "text/plain", "ok".toByteArray()) }
            createContext("/render") { exchange ->
                if (exchange.requestMethod !=
                    "POST"
                ) {
                    return@createContext exchange.respond(405, "text/plain", "POST an RC document".toByteArray())
                }
                val body = exchange.requestBody.use { it.readNBytes(MAX_DOCUMENT_BYTES + 1) }
                if (body.size >
                    MAX_DOCUMENT_BYTES
                ) {
                    return@createContext exchange.respond(413, "text/plain", "document too large".toByteArray())
                }
                val dark = exchange.requestURI.rawQuery?.split('&')?.any { it == "theme=dark" } == true
                val job = Job(body, dark).also(jobs::put)
                try {
                    exchange.respond(200, "image/png", job.result.get(RENDER_TIMEOUT_S, TimeUnit.SECONDS))
                } catch (e: java.util.concurrent.ExecutionException) {
                    val status = if (e.cause is IllegalArgumentException) 400 else 500
                    exchange.respond(status, "text/plain", (e.cause?.message ?: "render failed").toByteArray())
                } catch (_: java.util.concurrent.TimeoutException) {
                    exchange.respond(503, "text/plain", "render timed out".toByteArray())
                }
            }
        }
        // Warm up before accepting requests: the first render loads fonts and the player.
        renderer.render(warmUpDocument())
        server.start()
        println("preview-renderer listening on :$port")
        while (true) {
            val job = jobs.take()
            val started = System.nanoTime()
            try {
                job.result.complete(renderer.render(job.document, job.dark))
            } catch (e: Throwable) {
                job.result.completeExceptionally(e)
            }
            println("render ${job.document.size} B in ${(System.nanoTime() - started) / 1_000_000} ms")
        }
    }

    /** Built here, not loaded: always in the format of the bundled player (sdk-server of this build). */
    private fun warmUpDocument(): ByteArray = composeCast(imageLoader = { error("no images") }) {
        screen("warm-up") {
            text("compose-cast", style = "h1")
            button("Preview", Action.Reload)
        }
    }.render("warm-up")!!.document

    private fun HttpExchange.respond(status: Int, type: String, body: ByteArray) {
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    companion object {
        const val SERVE = "composecast.renderer.serve"
        const val DEFAULT_PORT = 8090
        const val MAX_DOCUMENT_BYTES = 16 * 1024 * 1024
        const val RENDER_TIMEOUT_S = 20L
    }
}
