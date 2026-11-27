package io.composecast.server.admin

import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentDesign
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.DesignElement
import io.composecast.protocol.DesignElementType
import io.composecast.server.sdk.CustomComponents
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AI-assisted custom component chat: Ollama & OpenAI-compatible payloads, vision images, validation. */
class ComponentAiTest {
    private val host = AdminTestHost()

    private fun validPromoJson(title: String = "Promo") = ComponentJson.encode(
        ComponentDefinition(
            id = "promo",
            title = title,
            rcVersion = CustomComponents.RC_VERSION,
            design = ComponentDesign(
                height = 120f,
                cornerRadius = 16f,
                elements = listOf(
                    DesignElement(id = "bg", type = DesignElementType.Rect, w = 360f, h = 120f, fill = "#3B5BDB"),
                ),
            ),
        ),
    )

    @Test
    fun `ollama chat sends schema prompt and inspiration image, validates and returns canonical JSON`() {
        var capturedUrl = ""
        var capturedBody = ""
        val replyJson = validPromoJson("AI Weather Card")
        val fakeAi = ComponentAiClient { url, _, body ->
            capturedUrl = url
            capturedBody = body
            200 to buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("role", "assistant")
                        put("content", "Here is your card:\n```json\n$replyJson\n```")
                    },
                )
            }.toString()
        }

        host.settings[AppKeys.AI_PROVIDER] = "ollama"
        host.settings[AppKeys.AI_BASE_URL] = "http://host.docker.internal:11434"
        host.settings[AppKeys.AI_MODEL] = "llama3.2-vision"
        host.components.saveDraft("promo", validPromoJson())

        host.run(withComponents = true, aiClient = fakeAi) { client ->
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            val page = client.get("/admin/components/promo/design") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
            assertContains(page, """id="ai-chat"""")
            assertContains(page, """/admin/components/component-ai.js""")

            val res = client.post("/admin/components/promo/ai/chat") {
                header(HttpHeaders.Cookie, cookie)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"currentJson": ${Json.encodeToString(
                        kotlinx.serialization.serializer<String>(),
                        validPromoJson(),
                    )},
                        "messages": [{"role":"user","content":"Make it a weather card","imageBase64":"iVBORw0KGgo=","imageMime":"image/png"}]}""",
                )
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals("http://host.docker.internal:11434/api/chat", capturedUrl)

            val sent = Json.parseToJsonElement(capturedBody).jsonObject
            assertEquals("llama3.2-vision", sent["model"]?.jsonPrimitive?.content)
            val msgs = sent["messages"]!!.jsonArray
            assertEquals("system", msgs[0].jsonObject["role"]?.jsonPrimitive?.content)
            assertContains(msgs[0].jsonObject["content"]!!.jsonPrimitive.content, "ComponentDefinition JSON schema")
            assertEquals("iVBORw0KGgo=", msgs[1].jsonObject["images"]!!.jsonArray[0].jsonPrimitive.content)

            val answer = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertTrue(answer["problems"]!!.jsonArray.isEmpty())
            assertContains(answer["json"]!!.jsonPrimitive.content, "AI Weather Card")
        }
    }

    @Test
    fun `openai-compatible provider sends bearer key and image_url, and reports validation problems`() {
        var capturedUrl = ""
        var capturedAuth = ""
        var capturedBody = ""
        val badJson = """{"id":"wrong-id","rcVersion":"${CustomComponents.RC_VERSION}",""" +
            """"design":{"width":10,"height":100,"elements":[]}}"""
        val fakeAi = ComponentAiClient { url, headers, body ->
            capturedUrl = url
            capturedAuth = headers["Authorization"].orEmpty()
            capturedBody = body
            200 to buildJsonObject {
                put(
                    "choices",
                    kotlinx.serialization.json.buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "message",
                                    buildJsonObject {
                                        put("role", "assistant")
                                        put("content", "```json\n$badJson\n```")
                                    },
                                )
                            },
                        )
                    },
                )
            }.toString()
        }

        host.settings[AppKeys.AI_PROVIDER] = "openai"
        host.settings[AppKeys.AI_BASE_URL] = "https://api.example.com/v1"
        host.settings[AppKeys.AI_MODEL] = "gpt-4o"
        host.settings[AppKeys.AI_API_KEY] = "sk-secret"
        host.components.saveDraft("promo", validPromoJson())

        host.run(withComponents = true, aiClient = fakeAi) { client ->
            val cookie = with(host) { client.loginAs(Role.EDITOR) }
            val res = client.post("/admin/components/promo/ai/chat") {
                header(HttpHeaders.Cookie, cookie)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"messages":[{"role":"user","content":"Redesign","imageBase64":"AAAA","imageMime":"image/jpeg"}]}""",
                )
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertEquals("https://api.example.com/v1/chat/completions", capturedUrl)
            assertEquals("Bearer sk-secret", capturedAuth)
            assertContains(capturedBody, "data:image/jpeg;base64,AAAA")

            val answer = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            val problems = answer["problems"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertTrue(problems.isNotEmpty(), "invalid generated JSON reports problems for Ask AI to fix")
        }
    }

    @Test
    fun `chat returns 400 when AI is not configured and 403 for viewers`() = host.run(withComponents = true) { client ->
        host.components.saveDraft("promo", validPromoJson())
        val editor = with(host) { client.loginAs(Role.EDITOR) }
        val viewer = with(host) { client.loginAs(Role.VIEWER) }

        val unconfigured = client.post("/admin/components/promo/ai/chat") {
            header(HttpHeaders.Cookie, editor)
            contentType(ContentType.Application.Json)
            setBody("""{"messages":[{"role":"user","content":"Hi"}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, unconfigured.status)
        assertContains(unconfigured.bodyAsText(), "not configured")

        val forbidden = client.post("/admin/components/promo/ai/chat") {
            header(HttpHeaders.Cookie, viewer)
            contentType(ContentType.Application.Json)
            setBody("""{"messages":[{"role":"user","content":"Hi"}]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    }
}
