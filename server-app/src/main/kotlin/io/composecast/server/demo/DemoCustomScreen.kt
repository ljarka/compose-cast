package io.composecast.server.demo

import io.composecast.protocol.ComponentJson
import io.composecast.server.sdk.RegistryBuilder

/** Ids of the demo's custom components (`examples/demo/components/<id>.json`, same files as resources). */
internal val demoComponentIds = listOf("parcel-tile", "promo-card")

/** The demo's custom components (ADR 0033), read from `server-app` resources `demo/components/`. */
internal fun RegistryBuilder.demoComponents() {
    demoComponentIds.forEach { id ->
        val text = checkNotNull(RegistryBuilder::class.java.getResource("/demo/components/$id.json")) {
            "missing resource demo/components/$id.json"
        }.readText()
        component(ComponentJson.decode(text, source = "demo/components/$id.json"))
    }
}

/** Tiles drawn by the `parcel-tile` custom component: raw RC JSON with vector icons from SVG paths. */
internal fun RegistryBuilder.customScreen() {
    screen("custom") {
        title = "Custom components"
        text("Custom components", style = "h2")
        text(
            "Each tile is one custom component written in raw Remote Compose JSON: a canvas with a " +
                "vector icon from an SVG path, a text on top and a tap action.",
            style = "caption",
        )
        spacer(12)
        grid(columns = 2, spacing = 12) {
            custom("parcel-tile", tile("Paczka w drodze", TRUCK, "navigate:data"))
            custom("parcel-tile", tile("Okazje zakupowe", TAG, "navigate:collections"))
            custom("parcel-tile", tile("Nadaj lub zwróć", PARCEL, "navigate:layouts"))
            custom("parcel-tile", tile("Zamówienia", RECEIPT, "navigate:dashboard") + ("accent" to "#FF8A65"))
        }
        spacer(16)
        text("Drawn in the component designer (shapes, texts and a tap area on an artboard):", style = "caption")
        spacer(8)
        custom(
            "promo-card",
            mapOf(
                "title" to "Free delivery",
                "subtitle" to "On every parcel sent this week from a parcel locker.",
                "cta" to "Send now",
                "action" to "navigate:data",
            ),
        )
    }
}

private fun tile(title: String, icon: String, action: String) = mapOf(
    "title" to title,
    "icon" to icon,
    "action" to action,
)

// Icons in a 48×48 box, stroked. RC path data knows only absolute M L H C S Q A Z (no V, no relative).
private const val TRUCK = "M4 12 L30 12 L30 34 L4 34 Z M30 20 L39 20 L46 28 L46 34 L30 34 M8 40 L16 40 M32 40 L40 40"
private const val TAG = "M6 6 L24 6 L42 24 L24 42 L6 24 Z M14 14 L16 16"
private const val PARCEL = "M8 16 L24 8 L40 16 L40 36 L24 44 L8 36 Z M8 16 L24 24 L40 16 M24 24 L24 44"
private const val RECEIPT =
    "M10 4 L38 4 L38 44 L33 40 L28 44 L24 40 L20 44 L15 40 L10 44 Z M16 14 L32 14 M16 22 L32 22 M16 30 L26 30"
