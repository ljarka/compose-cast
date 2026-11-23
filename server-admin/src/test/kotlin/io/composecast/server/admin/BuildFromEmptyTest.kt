package io.composecast.server.admin

import io.composecast.protocol.Component
import io.composecast.protocol.ScreenJson
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T-029 acceptance (C3): screens are built from empty with only what the visual editor offers —
 * "New screen", the screen form, "Add … to this list" and each component's form; no JSON editor.
 * The weather `main` of the T-025 app (fixtures/weather-main.json, ljarka/weather-app f0f729b) and
 * every demo screen come out byte-identical. The screen's maps (params, data, lookups) are JSON
 * fields of the screen form until the data source wizard (T-031).
 */
class BuildFromEmptyTest {
    private val host = AdminTestHost()

    /** Clicks a form/button of the editor; returns where the editor goes next (the selected node). */
    private suspend fun HttpClient.submit(cookie: String, url: String, fields: Map<String, String>): String {
        val response = submitForm(url, parameters { fields.forEach { (k, v) -> append(k, v) } }) {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals(HttpStatusCode.SeeOther, response.status, "$url $fields")
        return response.headers[HttpHeaders.Location]!!.substringAfter("node=").substringBefore('&')
            .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
    }

    private suspend fun HttpClient.build(cookie: String, target: JsonObject) {
        val id = (target["id"] as JsonPrimitive).content
        val visual = "/admin/screens/$id/visual"
        submit(cookie, "/admin/screens", mapOf("id" to id))
        submit(cookie, "$visual?node=", FormSchema.values(FormSchema.screenFields, target))

        suspend fun fill(list: String, items: JsonArray) {
            val current = (ScreenTree.get(currentDraft(id), list) as? JsonArray)?.size ?: 0
            items.forEachIndexed { i, item ->
                val node = item as JsonObject
                val type = (node["type"] as JsonPrimitive).content
                val path = submit(cookie, "$visual/edit?node=$list", mapOf("op" to "add-end", "type" to type))
                assertEquals("$list.${current + i}", path)
                val component = Json.decodeFromJsonElement(Component.serializer(), node)
                submit(
                    cookie,
                    "$visual?node=$path",
                    FormSchema.values(FormSchema.componentFields(type)!!, FormSchema.withDefaults(component)),
                )
                for (childList in FormSchema.componentLists(FormSchema.componentTypes.getValue(type))) {
                    fill("$path.$childList", node[childList] as? JsonArray ?: JsonArray(emptyList()))
                }
            }
            // A starter's own children (expandable, repeat) go last, once the real ones are there.
            repeat(current) { submit(cookie, "$visual/edit?node=$list.0", mapOf("op" to "delete")) }
        }
        fill("components", target["components"] as? JsonArray ?: JsonArray(emptyList()))
        (target["error"] as? JsonArray)?.let { fill("error", it) }
    }

    private fun currentDraft(id: String) = Json.parseToJsonElement(host.screens.get(id)!!.draftJson!!) as JsonObject

    private fun rebuilds(texts: List<String>) = host.run { client ->
        val cookie = with(host) { client.loginAs(Role.EDITOR) }
        for (text in texts) {
            val canonical = ScreenJson.encode(ScreenJson.decode(text, "target"))
            client.build(cookie, Json.parseToJsonElement(canonical) as JsonObject)
            val id = ScreenJson.decode(text, "target").id
            assertEquals(canonical, host.screens.get(id)!!.draftJson, "$id rebuilt in the visual editor")
        }
    }

    @Test
    fun `the weather app's main screen`() =
        rebuilds(listOf(javaClass.getResource("/fixtures/weather-main.json")!!.readText()))

    @Test
    fun `every demo screen`() {
        val demo = Path.of(System.getProperty("composecast.examples"), "demo", "screens").toFile()
        val files = demo.listFiles { f: File -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertEquals(true, files.size >= 8)
        rebuilds(files.map { it.readText() })
    }

    @Test
    fun `renaming a repeat's alias renames its uses`() {
        val template = Json.parseToJsonElement(
            """[{"type": "text", "text": "{{item.name | upper}} {{ item }} {{items.x}} {{itemx}}"}]""",
        )
        assertEquals(
            """[{"type":"text","text":"{{show.name | upper}} {{ show }} {{items.x}} {{itemx}}"}]""",
            Bindings.rename(template, "item", "show").toString(),
        )
    }
}
