package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A screen as authored: by the Kotlin DSL (`sdk-server`), by JSON files, later by the admin panel
 * (ADR 0011). This is the product's source format, independent of any renderer (ADR 0015): the
 * server turns it into a Remote Compose document; it is never sent to apps as-is.
 *
 * JSON form ([ScreenJson]): polymorphic types use the `"type"` discriminator, e.g.
 * `{"id":"main","components":[{"type":"text","text":"Hi","style":"h1"}]}`.
 */
@Serializable
data class ScreenDefinition(
    val id: String,
    val title: String = id,
    val components: List<Component> = emptyList(),
) {
    /**
     * Every problem in this definition, each prefixed with a JSON-like path
     * (`components[2].action: …`); empty when valid. The only validation of screen content —
     * used by the DSL, the JSON loader and (later) the admin panel.
     */
    fun problems(): List<String> = buildList {
        if (id.isBlank()) add("id: must not be blank")
        components.forEachIndexed { i, component ->
            val at = "components[$i]"
            when (component) {
                is Component.Text -> Unit
                is Component.Image -> if (component.url.isBlank()) add("$at.url: must not be blank")
                is Component.Button -> actionProblem(component.action)?.let { add("$at.action: $it") }
                is Component.Spacer ->
                    if (component.height < 0) add("$at.height: must be >= 0, was ${component.height}")
            }
        }
    }

    /** Throws [IllegalArgumentException] listing [problems], if any. */
    fun validate() {
        val problems = problems()
        require(problems.isEmpty()) { "Screen '$id': ${problems.joinToString("; ")}" }
    }

    private fun actionProblem(action: Action): String? {
        val encoded = ActionCodec.encode(action)
        return when {
            encoded.length > ActionCodec.MAX_ENCODED_LENGTH ->
                "encoded action is ${encoded.length} characters, Remote Compose allows ${ActionCodec.MAX_ENCODED_LENGTH}"
            // Catches empty screen/event names, empty param keys, empty URLs: anything the app
            // could not decode back to the same action.
            ActionCodec.decode(encoded) != action -> "$action cannot be carried to the app (empty name, key or URL?)"
            else -> null
        }
    }
}

/** A building block of a screen. New components: docs/playbooks/add-component.md. */
@Serializable
sealed interface Component {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val style: TextStyle = TextStyle.Body) : Component

    /** Fetched by the server and embedded in the document (ADR 0003). */
    @Serializable
    @SerialName("image")
    data class Image(val url: String, val alt: String = "") : Component

    @Serializable
    @SerialName("button")
    data class Button(val label: String, val action: Action) : Component

    /** Vertical space in dp. */
    @Serializable
    @SerialName("spacer")
    data class Spacer(val height: Int = 8) : Component
}

/** Text styles of [Component.Text]; [key] is the JSON / DSL name. */
@Serializable
enum class TextStyle(val key: String) {
    @SerialName("h1") H1("h1"),
    @SerialName("h2") H2("h2"),
    @SerialName("h3") H3("h3"),
    @SerialName("body") Body("body"),
    @SerialName("caption") Caption("caption"),
    ;

    companion object {
        fun fromKey(key: String): TextStyle =
            entries.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException("Unknown text style '$key'. Allowed: ${entries.joinToString { it.key }}")
    }
}

/** The JSON format of [ScreenDefinition]: strict (unknown keys are errors), defaults omitted. */
object ScreenJson {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(screen: ScreenDefinition): String = json.encodeToString(ScreenDefinition.serializer(), screen)

    /**
     * Decodes and validates one screen. Any problem throws [IllegalArgumentException] whose message
     * starts with [source] (e.g. the file name) and contains the JSON path.
     */
    fun decode(text: String, source: String): ScreenDefinition {
        val screen = try {
            json.decodeFromString(ScreenDefinition.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("$source: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$source: ${e.message}", e)
        }
        val problems = screen.problems()
        require(problems.isEmpty()) { "$source: ${problems.joinToString("; ")}" }
        return screen
    }
}
