package io.composecast.templates

import io.composecast.protocol.Action
import io.composecast.protocol.Component
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T-030: every bundled template is a valid, working app (ADR 0031). */
class AppTemplatesTest {
    private val images = ImageLoader { _ ->
        ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
    }

    @Test
    fun `the four app templates and Blank are bundled in order`() {
        assertEquals(listOf("news", "shop", "events", "weather", "blank"), AppTemplates.all.map { it.id })
        assertEquals("News", AppTemplates["news"]?.manifest?.name)
    }

    @Test
    fun `every template validates, builds like the server builds it and renders every screen with its fixtures`() {
        for (template in AppTemplates.all) {
            assertEquals(emptyList(), template.problems(), template.id)
            val data = DataLoader { request ->
                requireNotNull(template.fixture(request.url)) { "${template.id}: no fixture for ${request.url}" }
            }
            val registry = composeCast(images, data, env = { null }) {
                template.brand?.let { brand(it) }
                template.screens.forEach { screen(it) }
            }
            for (screen in template.screens) {
                val rendered = assertNotNull(registry.render(screen.id, RenderContext()), "${template.id}/${screen.id}")
                assertTrue(rendered.size > 0)
                val texts = texts(rendered.definition.components)
                // A binding that found nothing renders as empty text: the fixture or the path is wrong.
                assertTrue(texts.none { it.isBlank() }, "${template.id}/${screen.id}: empty texts in $texts")
            }
        }
    }

    @Test
    fun `a prefix renames screens and every navigation to them, nothing else`() {
        val news = assertNotNull(AppTemplates["news"]).withPrefix("blog-")
        assertEquals(listOf("blog-news", "blog-story"), news.screens.map { it.id })
        assertEquals("blog-news", news.manifest.home)
        assertEquals(emptyList(), news.problems())
        val card = (news.screens[0].components[0] as Component.Repeat).template[0]
        assertEquals("blog-story", (card.modifier.action as Action.Navigate).screen)
        val discussion = news.screens[1].components.last() as Component.Button
        assertTrue((discussion.action as Action.OpenUrl).url.startsWith("https://news.ycombinator.com"))
    }

    private fun texts(components: List<Component>): List<String> = components.flatMap { c ->
        val own = when (c) {
            is Component.Text -> listOf(c.text)
            is Component.Kpi -> listOf(c.value)
            else -> emptyList()
        }
        own + c.childLists().flatMap { (_, children) -> texts(children) }
    }
}
