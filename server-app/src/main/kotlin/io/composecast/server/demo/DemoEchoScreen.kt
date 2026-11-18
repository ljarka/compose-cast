package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.server.sdk.RegistryBuilder

/**
 * Rendered per request from its query parameters (`/screens/echo?word=…`) by Kotlin code: a DSL
 * dynamic screen has no JSON form (JSON screens use params/bindings instead, like `data`).
 */
internal fun RegistryBuilder.echoScreen() {
    dynamicScreen("echo") { ctx ->
        val word = ctx.param("word")
        title = "Echo"
        text("You sent", style = "caption")
        text(word, style = "h2")
        spacer(8)
        button("Again, louder", Action.Navigate("echo", mapOf("word" to "$word!")))
    }
}
