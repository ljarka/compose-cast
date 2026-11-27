package io.composecast.server.admin

import io.composecast.server.sdk.CustomComponents
import io.composecast.server.store.SettingsStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * AI assistant client for custom component creation/editing (`/admin/components/{id}/ai/chat`).
 * Supports Ollama (`/api/chat`) and OpenAI-compatible (`/chat/completions`) APIs, including
 * optional inspiration images (base64) for vision-capable models.
 */
internal data class AiConfig(val provider: String, val baseUrl: String, val model: String, val apiKey: String = "") {
    companion object {
        fun from(settings: SettingsStore): AiConfig? {
            val provider = settings[AppKeys.AI_PROVIDER]?.trim()?.lowercase().orEmpty()
            val baseUrl = settings[AppKeys.AI_BASE_URL]?.trim()?.trimEnd('/').orEmpty()
            val model = settings[AppKeys.AI_MODEL]?.trim().orEmpty()
            if (provider.isEmpty() || baseUrl.isEmpty() || model.isEmpty()) return null
            return AiConfig(
                provider = provider,
                baseUrl = baseUrl,
                model = model,
                apiKey = settings[AppKeys.AI_API_KEY]?.trim().orEmpty(),
            )
        }
    }
}

@Serializable
internal data class AiChatMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val imageMime: String? = null,
)

@Serializable
internal data class AiChatRequest(val currentJson: String = "", val messages: List<AiChatMessage> = emptyList())

/** HTTP transport seam so tests can verify Ollama/OpenAI payloads without network calls. */
internal fun interface AiHttpTransport {
    fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String>
}

internal class ComponentAiClient(private val transport: AiHttpTransport = DefaultTransport) {
    fun chat(
        config: AiConfig,
        componentId: String,
        currentJson: String,
        brandColorsJson: String,
        messages: List<AiChatMessage>,
    ): String {
        val systemPrompt = buildSystemPrompt(componentId, currentJson, brandColorsJson)
        val trimmed = messages.takeLast(MAX_HISTORY_MESSAGES)
        return when (config.provider) {
            "ollama" -> callOllama(config, systemPrompt, trimmed)
            "openai" -> callOpenAi(config, systemPrompt, trimmed)
            else -> throw IllegalArgumentException("Unsupported AI provider '${config.provider}'")
        }
    }

    private fun callOllama(config: AiConfig, systemPrompt: String, messages: List<AiChatMessage>): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                    )
                    messages.forEach { msg ->
                        add(
                            buildJsonObject {
                                put("role", if (msg.role == "assistant") "assistant" else "user")
                                put("content", msg.content)
                                if (!msg.imageBase64.isNullOrBlank()) {
                                    put(
                                        "images",
                                        buildJsonArray {
                                            add(JsonPrimitive(msg.imageBase64))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
        val headers = buildMap {
            put("Content-Type", "application/json")
            if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
        }
        val url = "${config.baseUrl}/api/chat"
        val (status, body) = transport.postJson(url, headers, payload.toString())
        check(status in 200..299) { "Ollama returned HTTP $status: ${body.take(300)}" }
        val root = Json.parseToJsonElement(body).jsonObject
        val content = root["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return checkNotNull(content?.takeIf { it.isNotBlank() }) { "Ollama returned an empty response" }
    }

    private fun callOpenAi(config: AiConfig, systemPrompt: String, messages: List<AiChatMessage>): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                    )
                    messages.forEach { msg ->
                        add(
                            buildJsonObject {
                                put("role", if (msg.role == "assistant") "assistant" else "user")
                                if (!msg.imageBase64.isNullOrBlank() && msg.role != "assistant") {
                                    val mime = msg.imageMime?.takeIf { it in ALLOWED_IMAGE_MIMES } ?: "image/png"
                                    put(
                                        "content",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", msg.content)
                                                },
                                            )
                                            add(
                                                buildJsonObject {
                                                    put("type", "image_url")
                                                    put(
                                                        "image_url",
                                                        buildJsonObject {
                                                            put("url", "data:$mime;base64,${msg.imageBase64}")
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                } else {
                                    put("content", msg.content)
                                }
                            },
                        )
                    }
                },
            )
        }
        val headers = buildMap {
            put("Content-Type", "application/json")
            if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
        }
        val url = if (config.baseUrl.endsWith("/chat/completions")) {
            config.baseUrl
        } else {
            "${config.baseUrl}/chat/completions"
        }
        val (status, body) = transport.postJson(url, headers, payload.toString())
        check(status in 200..299) { "AI provider returned HTTP $status: ${body.take(300)}" }
        val root = Json.parseToJsonElement(body).jsonObject
        val choices = root["choices"] as? JsonArray
        val content = choices?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
        return checkNotNull(content?.takeIf { it.isNotBlank() }) { "AI provider returned an empty response" }
    }

    companion object {
        const val MAX_HISTORY_MESSAGES = 16
        const val MAX_IMAGE_BASE64_CHARS = 7_000_000 // ~5 MB binary
        val ALLOWED_IMAGE_MIMES = setOf("image/png", "image/jpeg", "image/webp")

        private val FENCED_JSON = Regex("""```(?:json)?\s*\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)

        private val DefaultTransport = AiHttpTransport { url, headers, body ->
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body))
            headers.forEach { (k, v) -> builder.header(k, v) }
            val response = try {
                client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            } catch (e: Exception) {
                throw IllegalStateException("Could not reach AI provider: ${e.message}", e)
            }
            response.statusCode() to response.body()
        }

        /** Extracts the last valid JSON object from a fenced ```json``` block or a raw JSON response. */
        fun extractJsonObject(reply: String): String? {
            val fenced = FENCED_JSON.findAll(reply).map { it.groupValues[1].trim() }.toList()
            for (candidate in fenced.asReversed()) {
                if (isJsonObject(candidate)) return candidate
            }
            val trimmed = reply.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}") && isJsonObject(trimmed)) return trimmed
            val first = trimmed.indexOf('{')
            val last = trimmed.lastIndexOf('}')
            if (first >= 0 && last > first) {
                val slice = trimmed.substring(first, last + 1)
                if (isJsonObject(slice)) return slice
            }
            return null
        }

        private fun isJsonObject(text: String): Boolean =
            runCatching { Json.parseToJsonElement(text) is JsonObject }.getOrDefault(false)

        /**
         * Complete, self-contained schema reference for compose-cast custom components so the model
         * knows every valid field, enum, placeholder, and rule from the very first message.
         */
        fun buildSystemPrompt(componentId: String, currentJson: String, brandColorsJson: String): String = """
You are an expert UI component designer for compose-cast (Android Remote Compose).
Your job is to create or modify the JSON definition of custom component "$componentId".

### Response rules
1. Reply concisely in the user's language, explaining what you created or changed.
2. ALWAYS include the COMPLETE updated component JSON inside a single ```json ... ``` fenced block.
3. Keep `"id": "$componentId"` and `"rcVersion": "${CustomComponents.RC_VERSION}"` unchanged.
4. Prefer the `design` format (artboard-based, editable in the visual Designer) unless the current component uses raw Remote Compose `body` without `design` or the user explicitly asks for raw RC JSON.
5. Every `{{params.NAME}}` used anywhere in `design` or `body` MUST be declared in `params`.
6. Colors must be `#RRGGBB`, `#AARRGGBB`, `{{params.NAME}}`, or `{{brand.FIELD}}`.
   Valid brand fields: `primaryColor`, `secondaryColor`, `backgroundColor`, `surfaceColor`, `textColor`, `outlineColor`.

### ComponentDefinition JSON schema
```json
{
  "id": "$componentId",
  "title": "Human readable title",
  "description": "Short description",
  "version": 1,
  "rcVersion": "${CustomComponents.RC_VERSION}",
  "minApiLevel": 8,
  "params": {
    "title": { "type": "text", "default": "Hello", "description": "Main heading" },
    "accent": { "type": "color", "default": "#3B5BDB", "description": "Accent color" },
    "score": { "type": "number", "default": "42", "description": "Numeric value" },
    "onTap": { "type": "action", "default": "reload:", "description": "Tap action" }
  },
  "design": {
    "width": 360,
    "height": 140,
    "cornerRadius": 16,
    "elements": [ ... ]
  }
}
```

### `design` schema (preferred — compiles automatically to Remote Compose)
- `width`: number, 40..2000 dp (default 360). Artboard reference width (e.g. 360 for full width, 172 for half width).
- `height`: number, 8..2000 dp (default 160). Component height on screen.
- `fillWidth`: number, 0.1..1.0 (default 1.0 = full width; 0.5 = half width widget).
- `cornerRadius`: number >= 0 dp. Clips the whole component.
- `elements`: array of up to 200 elements, drawn bottom-to-top (first = background, last = foreground).

Each element in `design.elements`:
- `id` (required, unique string, e.g. `"bg"`, `"badge"`, `"title"`)
- `name` (label in layer list)
- `type` (required): `"rect"` | `"ellipse"` | `"line"` | `"path"` | `"text"` | `"tap"`
- `x`, `y`, `w`, `h`: numbers in dp (for `rect`/`ellipse`/`path`/`text`/`tap`, `w >= 0`, `h >= 0`; for `line`, `(x, y)` to `(x+w, y+h)` may be negative)
- `anchor`: `"left"` (default) | `"right"` | `"center"` | `"stretch"` (use `"stretch"` for full-width backgrounds/headers/texts so they adapt to any phone width!)
- `fill`: color string (`#RRGGBB`, `#AARRGGBB`, `{{params.x}}`, `{{brand.x}}`); for `text`, this is the text color
- `fill2`: optional second color for a linear gradient from `fill` to `fill2`
- `gradientAngle`: degrees (0 = left-to-right, 90 = top-to-bottom, default 90)
- `stroke`: optional border/outline color
- `strokeWidth`: number >= 0 dp
- `radius`: corner radius in dp (for `rect`)
- `opacity`: 0..1 (default 1)
- `rotation`: degrees clockwise around element center (shapes only)
- `path`: SVG path data for `type: "path"` using ONLY absolute commands `M L H C S Q A Z`
- `pathWidth`, `pathHeight`: optional viewBox size of `path` (e.g. 24 and 24 for a 24x24 icon; scaled to `w` x `h`)
- `text`: string for `type: "text"` (may contain `{{params.x}}`)
- `fontSize`: number in dp (default 16)
- `bold`: boolean (default false)
- `textAlign`: `"start"` (default) | `"center"` | `"end"`
- `maxLines`: integer >= 1 (default 1)
- `action`: optional wire action on tap, e.g. `"navigate:detail"`, `"reload:"`, `"open_url:https://example.com"`, or `"{{params.onTap}}"`
- `hidden`: boolean (default false)

### Param types (`params.<name>.type`)
- `"text"`: any string
- `"color"`: `#RRGGBB` or `#AARRGGBB`
- `"number"`: finite number string (e.g. `"42"`)
- `"action"`: wire action (`navigate:<screen>`, `open_url:<url>`, `reload:`, `back:`)

### Current app brand colors
$brandColorsJson

### Current component JSON
```json
$currentJson
```
        """.trimIndent()
    }
}
