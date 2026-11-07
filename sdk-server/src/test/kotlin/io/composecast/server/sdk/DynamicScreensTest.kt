package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DynamicScreensTest {
    private val registry = composeCast(imageLoader = { error("no images") }) {
        screen("main") { text("Static") }
        dynamicScreen("echo") { ctx ->
            title = "Echo ${ctx.param("word")}"
            text(ctx.param("word"), style = "h2")
            button("Again", Action.Navigate("echo", mapOf("word" to ctx.param("word") + "!")))
        }
    }

    private fun text(screen: RemoteScreen) = String(screen.document, Charsets.UTF_8)

    @Test
    fun `dynamic screen renders its params`() {
        val screen = registry.render("echo", RenderContext(mapOf("word" to "zażółć")))!!
        assertEquals("Echo zażółć", screen.title)
        assertTrue("zażółć" in text(screen))
        assertTrue("navigate:echo?word=za%C5%BC%C3%B3%C5%82%C4%87%21" in text(screen))
    }

    @Test
    fun `static screens are cached and ignore params, dynamic ones are not pre-rendered`() {
        assertSame(registry.get("main"), registry.render("main", RenderContext(mapOf("x" to "y"))))
        assertNull(registry.get("echo"))
        assertTrue(registry.isDynamic("echo"))
        assertFalse(registry.isDynamic("main"))
        assertEquals(listOf("main", "echo"), registry.ids())
        assertEquals(setOf("main"), registry.all().keys)
        assertNull(registry.render("nope"))
    }

    @Test
    fun `missing param is a ScreenParameterException`() {
        val e = assertFailsWith<ScreenParameterException> { registry.render("echo") }
        assertTrue("word" in e.message!!)
    }

    @Test
    fun `invalid dynamic content fails on render, duplicate ids at build`() {
        val bad = composeCast { dynamicScreen("bad") { ctx -> spacer(ctx.params["h"]!!.toInt()) } }
        assertEquals(
            listOf(Component.Spacer(8)),
            bad.render("bad", RenderContext(mapOf("h" to "8")))!!.definition.components,
        )
        assertFailsWith<IllegalArgumentException> { bad.render("bad", RenderContext(mapOf("h" to "-1"))) }
        assertFailsWith<IllegalArgumentException> {
            composeCast {
                screen("a") {}
                dynamicScreen("a") {}
            }
        }
    }

    @Test
    fun `etag is stable for equal output and changes with params`() {
        fun etag(word: String) = registry.render("echo", RenderContext(mapOf("word" to word)))!!.etag
        assertEquals(etag("a"), etag("a"))
        assertNotEquals(etag("a"), etag("b"))
        assertEquals(32, etag("a").length)
    }

    @Test
    fun `concurrent renders succeed and are identical`() {
        val pool = Executors.newFixedThreadPool(16)
        try {
            val results = pool.invokeAll(
                (1..50).map { i ->
                    Callable { registry.render("echo", RenderContext(mapOf("word" to "w${i % 5}")))!! }
                },
            ).map { it.get() }
            results.groupBy { it.title }.values.forEach { same ->
                assertEquals(1, same.map { it.etag }.toSet().size, "different bytes for ${same.first().title}")
            }
        } finally {
            pool.shutdown()
        }
    }
}
