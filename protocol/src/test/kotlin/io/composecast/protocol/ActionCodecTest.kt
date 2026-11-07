package io.composecast.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionCodecTest {

    private val tricky = linkedMapOf(
        "q" to "a&b=c?d",
        "plus" to "1+1",
        "space" to "hello world",
        "pl" to "zażółć gęślą jaźń",
        "empty" to "",
    )

    private val all = listOf(
        Action.Navigate("onboarding"),
        Action.Navigate("movie/details", tricky),
        Action.Navigate("ekran ?&=", mapOf("k" to "v")),
        Action.OpenUrl("https://x.dev/p?a=b&c=d#frag"),
        Action.Reload,
        Action.Track("button_tap"),
        Action.Track("search", tricky),
        Action.Custom("hello"),
        Action.Custom("a:b?c=d&e"),
        Action.Custom(""),
    )

    @Test
    fun `every action round-trips`() {
        all.forEach { assertEquals(it, ActionCodec.decode(ActionCodec.encode(it)), "round trip of $it") }
    }

    @Test
    fun `params keep insertion order, encoding is deterministic`() {
        val a = Action.Navigate("s", linkedMapOf("b" to "2", "a" to "1"))
        assertEquals("navigate:s?b=2&a=1", ActionCodec.encode(a))
        assertEquals(listOf("b", "a"), (ActionCodec.decode(ActionCodec.encode(a)) as Action.Navigate).params.keys.toList())
    }

    @Test
    fun `wire format matches the documented grammar`() {
        assertEquals("navigate:onboarding", ActionCodec.encode(Action.Navigate("onboarding")))
        assertEquals("open_url:https://x.dev/p?a=b", ActionCodec.encode(Action.OpenUrl("https://x.dev/p?a=b")))
        assertEquals("reload:", ActionCodec.encode(Action.Reload))
        assertEquals("track:tap?id=7", ActionCodec.encode(Action.Track("tap", mapOf("id" to "7"))))
        assertEquals("custom:hello", ActionCodec.encode(Action.Custom("hello")))
    }

    @Test
    fun `documents written before typed actions still decode`() {
        assertEquals(Action.Navigate("onboarding"), ActionCodec.decode("navigate:onboarding"))
        assertEquals(Action.OpenUrl("https://example.com"), ActionCodec.decode("open_url:https://example.com"))
        assertEquals(Action.Custom("anything"), ActionCodec.decode("custom:anything"))
    }

    @Test
    fun `unknown or malformed actions decode to null without throwing`() {
        listOf(
            "future_type:x", "navigate", "", ":", "navigate:", "navigate:a?%zz", "navigate:a?novalue",
            "navigate:a?=v", "track:", "open_url:", "reload:now", "%zz:x",
        ).forEach { assertNull(ActionCodec.decode(it), "'$it' should not decode") }
    }

    @Test
    fun `actions serialize to JSON with a type discriminator`() {
        val json = Json
        all.forEach { action ->
            val text = json.encodeToString(Action.serializer(), action)
            assertEquals(action, json.decodeFromString(Action.serializer(), text), text)
        }
        assertEquals(
            """{"type":"navigate","screen":"onboarding"}""",
            json.encodeToString(Action.serializer(), Action.Navigate("onboarding")),
        )
    }
}
