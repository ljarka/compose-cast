package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataBindingTest {
    private val forecast = """
        {"current":{"temperature_2m":21.46,"weather_code":2,"time":"2026-11-08T14:00"},
         "current_units":{"temperature_2m":"°C"},
         "daily":{"time":["2026-11-08","2026-11-09","2026-11-10"],"max":[22.0,18.5],"min":[11,9.25,7]},
         "shows":[{"name":"Dune","id":42},{"name":"Fargo","id":7}],
         "empty":[], "flag":true, "nothing":null}
    """.trimIndent()

    /** Records requests; answers [body] or throws [failure]. */
    private class FakeLoader(var body: String = "{}", var failure: String? = null, val delayMs: Long = 0) : DataLoader {
        val calls = AtomicInteger()
        val requests = mutableListOf<DataRequest>()

        override fun load(request: DataRequest): String {
            calls.incrementAndGet()
            synchronized(requests) { requests += request }
            if (delayMs > 0) Thread.sleep(delayMs)
            failure?.let { throw IllegalStateException(it) }
            return body
        }
    }

    private fun registry(loader: DataLoader, env: Map<String, String> = emptyMap(), block: ScreenBuilder.() -> Unit) =
        composeCast(imageLoader = { error("no images") }, dataLoader = loader, env = env::get) { screen("s", block) }

    private fun texts(registry: ScreenRegistry, params: Map<String, String> = emptyMap()): List<String> =
        registry.render("s", RenderContext(params))!!.definition.components.map {
            when (it) {
                is Component.Text -> it.text
                is Component.Button -> "[${it.label}] ${it.action}"
                else -> it.toString()
            }
        }

    @Test
    fun `binds paths, filters and number formatting`() {
        val r = registry(FakeLoader(forecast)) {
            data("w", url = "https://api.dev/forecast")
            lookup("wmo", mapOf("2" to "Partly cloudy", "*" to "Unknown"))
            text("{{w.current.temperature_2m | round}}{{w.current_units.temperature_2m}}")
            text("{{w.current.temperature_2m | round:1}} {{w.daily.max[0]}} {{w.daily.min[0]}} {{w.daily.min[1]}}")
            text("{{w.current.weather_code | lookup:wmo}} / {{w.flag | lookup:wmo}}")
            text(
                "{{w.current.time | date:'EEE HH:mm'}} {{w.daily.time[0] | date:'EEEE d MMM'}} {{w.daily.time[0] | date:'HH:mm'}}|",
            )
            text(
                "{{w.missing.deep}}|{{w.nothing | default:'n/a'}}|{{w.shows[1].name | upper}}|{{w.shows[9].name | default:'?'}}",
            )
            text("{{w.shows[0]}}")
        }
        assertEquals(
            listOf(
                "21°C",
                "21.5 22 11 9.25",
                "Partly cloudy / Unknown",
                "Sun 14:00 Sunday 8 Nov |",
                "|n/a|FARGO|?",
                """{"name":"Dune","id":42}""",
            ),
            texts(r),
        )
        assertTrue(r.isBound("s"))
    }

    @Test
    fun `repeat over an array, zipped parallel arrays, limit, index and empty`() {
        val r = registry(FakeLoader(forecast)) {
            data("w", url = "https://api.dev/x")
            repeat("w.shows", alias = "show") {
                button("{{\$index}}. {{show.name}}", Action.Navigate("show", mapOf("id" to "{{show.id}}")))
            }
            // max has 2 elements: zip stops at the shortest array
            repeat(zip = mapOf("day" to "w.daily.time", "max" to "w.daily.max"), alias = "d") {
                text("{{d.day | date:'EEE'}} {{d.max | round}}")
            }
            repeat("w.daily.time", limit = 1) { text("first {{item}}") }
            repeat("w.empty", empty = { text("none") }) { text("never") }
            repeat("w.missing", empty = { text("missing too") }) { text("never") }
        }
        assertEquals(
            listOf(
                "[0. Dune] Navigate(screen=show, params={id=42})",
                "[1. Fargo] Navigate(screen=show, params={id=7})",
                "Sun 22",
                "Mon 19",
                "first 2026-11-08",
                "none",
                "missing too",
            ),
            texts(r),
        )
    }

    @Test
    fun `params - defaults, query values, required, url encoding`() {
        val loader = FakeLoader(forecast)
        val r = registry(loader) {
            title = "{{params.city}}"
            param("city", default = "Berlin")
            param("q")
            data("w", url = "https://api.dev/search?q={{params.q}}&city={{params.city}}")
            text("{{params.q}}")
        }
        assertFailsWith<ScreenParameterException> { r.render("s") }
        val screen = r.render("s", RenderContext(mapOf("q" to "a b&c=ż")))!!
        assertEquals("Berlin", screen.title)
        assertEquals("a b&c=ż", (screen.definition.components.single() as Component.Text).text)
        assertEquals("https://api.dev/search?q=a%20b%26c%3D%C5%BC&city=Berlin", loader.requests.single().url)
    }

    @Test
    fun `sources are fetched in parallel and cached per url`() {
        val loader = FakeLoader(forecast, delayMs = 300)
        val r = registry(loader) {
            param("p", default = "1")
            data("a", url = "https://a.dev/?p={{params.p}}", cacheSeconds = 60)
            data("b", url = "https://b.dev/", cacheSeconds = 0)
            text("{{a.flag}} {{b.flag}}")
        }
        val started = System.nanoTime()
        assertEquals(listOf("true true"), texts(r))
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(millis < 550, "two 300 ms sources took $millis ms: not parallel")
        assertEquals(2, loader.calls.get())

        texts(r) // a cached (60 s), b not cached (0 s)
        assertEquals(3, loader.calls.get())
        texts(r, mapOf("p" to "2")) // other url → a fetched again
        assertEquals(5, loader.calls.get())
    }

    @Test
    fun `failing source - error components, 502 exception, optional, timeout, failure cached`() {
        val loader = FakeLoader(failure = "HTTP 503")
        val withError = registry(loader) {
            param("city", default = "Oslo")
            data("w", url = "https://api.dev/")
            text("{{w.temp}}")
            error {
                text("No data for {{params.city}}")
                button("Retry", Action.Reload)
            }
        }
        assertEquals(listOf("No data for Oslo", "[Retry] Reload"), texts(withError))
        texts(withError)
        assertEquals(1, loader.calls.get(), "failures are cached for 10 s")

        val withoutError = registry(FakeLoader(failure = "HTTP 503")) {
            data("w", url = "https://api.dev/")
            text("{{w.temp}}")
        }
        val e = assertFailsWith<DataSourceException> { withoutError.render("s") }
        assertEquals("data source 'w' failed: HTTP 503", e.message)

        val optional = registry(FakeLoader(failure = "down")) {
            data("w", url = "https://api.dev/", optional = true)
            text("{{w.temp | default:'–'}}")
        }
        assertEquals(listOf("–"), texts(optional))

        val slow = registry(FakeLoader(forecast, delayMs = 2_000)) {
            data("w", url = "https://api.dev/", timeoutMs = 100)
            text("{{w.flag}}")
        }
        assertEquals(
            "data source 'w' failed: timeout after 100 ms",
            assertFailsWith<DataSourceException> {
                slow.render("s")
            }.message,
        )
    }

    @Test
    fun `secrets - resolved in url and headers only, required at load, masked in errors`() {
        val loader = FakeLoader(failure = "rejected key s3cr3t!")
        val env = mapOf("COMPOSECAST_SECRET_KEY" to "s3cr3t!", "COMPOSECAST_SECRET_TOKEN" to "tok")
        val r = registry(loader, env) {
            data(
                "w",
                url = "https://api.dev/?key={{secret.KEY}}",
                headers = mapOf("Authorization" to "Bearer {{secret.TOKEN}}"),
            )
            text("{{w.a}}")
        }
        val e = assertFailsWith<DataSourceException> { r.render("s") }
        assertEquals("data source 'w' failed: rejected key ***", e.message)
        assertEquals("https://api.dev/?key=s3cr3t%21", loader.requests.single().url)
        assertEquals(mapOf("Authorization" to "Bearer tok"), loader.requests.single().headers)

        val missing = assertFailsWith<IllegalArgumentException> {
            registry(loader) { data("w", url = "https://api.dev/?key={{secret.KEY}}") }
        }
        assertTrue("COMPOSECAST_SECRET_KEY is not set" in missing.message!!, missing.message)

        val inText = assertFailsWith<IllegalArgumentException> {
            registry(loader, env) {
                data("w", url = "https://api.dev/")
                text("{{secret.KEY}}")
            }
        }
        assertTrue("secrets are only allowed" in inText.message!!, inText.message)
    }

    @Test
    fun `static screens stay pre-rendered, invalid date pattern fails at load, json text is not re-parsed`() {
        val loader = FakeLoader("""{"t":"literal {{braces}} from the API"}""")
        val r = composeCast(imageLoader = { error("no images") }, dataLoader = loader) {
            screen("plain") { text("x") }
            screen("s") {
                data("w", url = "https://api.dev/")
                text("{{w.t}}")
            }
        }
        assertFalse(r.isBound("plain"))
        assertTrue(r.get("plain") != null && r.get("s") == null)
        assertEquals(listOf("literal {{braces}} from the API"), texts(r))
        assertEquals(setOf("plain", "s"), r.definitions().keys)

        val e = assertFailsWith<IllegalArgumentException> {
            registry(loader) {
                data("w", url = "https://api.dev/")
                text("{{w.t | date:'bad pattern {'}}")
            }
        }
        assertTrue("invalid date pattern" in e.message!!, e.message)
    }

    @Test
    fun `dsl and json produce the same definition`() {
        val dsl = registry(FakeLoader()) {
            param("q", default = "x")
            data("w", url = "https://api.dev/?q={{params.q}}", cacheSeconds = 600)
            lookup("c", mapOf("*" to "?"))
            repeat(zip = mapOf("a" to "w.a"), alias = "d", limit = 2, empty = {
                text("none")
            }) { text("{{d.a | lookup:c}}") }
            error { text("down") }
        }.definitions().getValue("s")
        val json = io.composecast.protocol.ScreenJson.encode(dsl)
        assertEquals(dsl, io.composecast.protocol.ScreenJson.decode(json, "s.json"))
        assertEquals(ScreenDefinition::class, dsl::class)
    }
}
