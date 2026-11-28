package io.composecast.android

import androidx.compose.runtime.saveable.SaverScope
import io.composecast.protocol.Action
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeCastNavigatorTest {
    @Test
    fun `navigate pushes, pop goes back but keeps the root`() {
        val nav = ComposeCastNavigator(listOf(Action.Navigate("main")))
        assertFalse(nav.canGoBack)
        nav.navigate("movie", mapOf("id" to "42"))
        nav.navigate(Action.Navigate("cast"))
        assertEquals(Action.Navigate("cast"), nav.current)
        assertTrue(nav.canGoBack)
        assertTrue(nav.pop())
        assertEquals(Action.Navigate("movie", mapOf("id" to "42")), nav.current)
        assertTrue(nav.pop())
        assertFalse(nav.pop())
        assertEquals(listOf(Action.Navigate("main")), nav.backStack)
    }

    @Test
    fun `replaceRoot clears the back stack and reload counts`() {
        val nav = ComposeCastNavigator()
        assertNull(nav.current)
        nav.replaceRoot("home", mapOf("city" to "Warsaw"))
        nav.navigate("detail")
        nav.replaceRoot("other")
        assertEquals(listOf(Action.Navigate("other")), nav.backStack)
        nav.reload()
        nav.reload()
        assertEquals(2, nav.reloadCount)
    }

    @Test
    fun `saver round-trips the back stack through ActionCodec strings`() {
        val nav = ComposeCastNavigator(
            listOf(Action.Navigate("main"), Action.Navigate("movie", mapOf("id" to "42", "q" to "a b&c=d"))),
        )
        val saved = with(ComposeCastNavigator.Saver) { SaverScope { true }.save(nav) }!!
        assertEquals(listOf("navigate:main", "navigate:movie?id=42&q=a+b%26c%3Dd"), saved.toList())
        val restored = ComposeCastNavigator.Saver.restore(saved)!!
        assertEquals(nav.backStack, restored.backStack)
    }
}
