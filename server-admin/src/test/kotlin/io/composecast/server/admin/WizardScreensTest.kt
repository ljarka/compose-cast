package io.composecast.server.admin

import io.composecast.protocol.ScreenJson
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.composeCast
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** T-031 W2: generated list + detail screens are valid and render with the fixture APIs' data. */
class WizardScreensTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/apis/$name")!!.readText()

    private val png = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    /** The plan the wizard pre-fills from the guesses (as the page does). */
    private fun plan(fixture: String, url: String, detailUrl: String?): WizardScreens.Plan {
        val list = ApiShape.lists(Json.parseToJsonElement(fixture)).first()
        val roles = ApiShape.roles(list.fields)
        return WizardScreens.Plan(
            source = "api", url = url, headers = emptyMap(), listPath = list.path,
            id = roles.id, title = roles.title!!, subtitle = roles.subtitle, image = roles.image,
            html = list.fields.filter { '<' in it.sample }.map { it.path }.toSet(),
            listScreen = "items", listTitle = "Items", detailScreen = "item", detailUrl = detailUrl,
            sampleId = list.fields.firstOrNull { it.path == roles.id }?.sample,
        )
    }

    private fun render(plan: WizardScreens.Plan, responses: Map<String, String>): Pair<String, String> {
        val registry = composeCast(ImageLoader { png }, DataLoader { request -> responses.getValue(request.url) }) {
            screen(WizardScreens.list(plan))
            screen(WizardScreens.detail(plan))
        }
        // The bound definition (what was rendered, bindings resolved) carries the full texts; the
        // document dump shortens them.
        val list = registry.render(plan.listScreen, RenderContext(emptyMap()))!!
        val detail = registry.render(plan.detailScreen, RenderContext(emptyMap()))!!
        assertContains(list.describe(), "IMAGE_LAYOUT".takeIf { plan.image != null } ?: "COLUMN")
        return ScreenJson.encode(list.definition) to ScreenJson.encode(detail.definition)
    }

    @Test
    fun `TVMaze - list with images and a detail from the show endpoint`() {
        val shows = fixture("tvmaze-shows.json")
        val first = (Json.parseToJsonElement(shows) as JsonArray).first() as JsonObject
        val plan = plan(shows, "https://api.tvmaze.com/shows", "https://api.tvmaze.com/shows/{{params.id}}")
        assertEquals(setOf("summary"), plan.html)
        val (list, detail) = render(
            plan,
            mapOf(
                "https://api.tvmaze.com/shows" to shows,
                "https://api.tvmaze.com/shows/1" to first.toString(),
            ),
        )
        assertContains(list, "Under the Dome")
        assertFalse("<p>" in list, "summaries go through plain")
        assertContains(detail, "Under the Dome", message = "params.id defaults to the first show")
    }

    @Test
    fun `Rick and Morty - list under results, detail endpoint by id`() {
        val characters = fixture("rickandmorty-characters.json")
        val plan =
            plan(
                characters,
                "https://rickandmortyapi.com/api/character",
                "https://rickandmortyapi.com/api/character/{{params.id}}",
            )
        assertEquals("results", plan.listPath)
        val first = ((Json.parseToJsonElement(characters) as JsonObject)["results"] as JsonArray).first().toString()
        val (list, detail) = render(
            plan,
            mapOf(
                plan.url to characters,
                "https://rickandmortyapi.com/api/character/1" to first,
            ),
        )
        assertContains(list, "Rick Sanchez")
        assertContains(detail, "Rick Sanchez")
    }

    @Test
    fun `JSONPlaceholder - no image, no detail endpoint - the list passes its fields`() {
        val posts = fixture("jsonplaceholder-posts.json")
        val plan = plan(posts, "https://jsonplaceholder.typicode.com/posts", detailUrl = null)
        val list = WizardScreens.list(plan)
        val (rendered, _) = render(plan, mapOf(plan.url to posts))
        assertContains(rendered, "sunt aut facere")
        assertEquals(emptyMap(), WizardScreens.detail(plan).data)
        assertEquals(setOf("title", "subtitle"), WizardScreens.detail(plan).params.keys)
        assertEquals(emptyList(), list.problems())
    }
}
