package io.composecast.server.admin

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** T-031: list finding and field roles on three real API shapes (trimmed copies in fixtures/apis). */
class ApiShapeTest {
    private fun fixture(name: String) =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/apis/$name")!!.readText())

    @Test
    fun `TVMaze shows - a top-level array with nested images and HTML summaries`() {
        val list = ApiShape.lists(fixture("tvmaze-shows.json")).first()
        assertEquals("" to 4, list.path to list.size)
        assertEquals(
            ApiShape.Roles(id = "id", title = "name", subtitle = "summary", image = "image.medium"),
            ApiShape.roles(list.fields),
        )
        assertEquals(ApiShape.Kind.LongText, list.fields.single { it.path == "summary" }.kind)
        assertEquals(ApiShape.Kind.Date, list.fields.single { it.path == "premiered" }.kind)
    }

    @Test
    fun `Rick and Morty characters - the list is under results, next to paging info`() {
        val lists = ApiShape.lists(fixture("rickandmorty-characters.json"))
        assertEquals(listOf("results"), lists.map { it.path })
        assertEquals(
            ApiShape.Roles(id = "id", title = "name", subtitle = "species", image = "image"),
            ApiShape.roles(lists.single().fields),
        )
    }

    @Test
    fun `JSONPlaceholder posts - no images`() {
        val list = ApiShape.lists(fixture("jsonplaceholder-posts.json")).single()
        assertEquals(
            ApiShape.Roles(id = "id", title = "title", subtitle = "body", image = null),
            ApiShape.roles(list.fields),
        )
    }

    @Test
    fun `no lists of objects - nothing to offer`() {
        assertEquals(emptyList(), ApiShape.lists(Json.parseToJsonElement("""{"a": [1, 2], "b": {"c": "x"}}""")))
    }
}
