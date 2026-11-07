package io.composecast.server.sdk

import io.composecast.protocol.ComponentModel
import io.composecast.protocol.ScreenModel

@DslMarker
annotation class ScreenDslMarker

@ScreenDslMarker
class ScreenBuilder(val id: String) {
    var title: String = id
    private val components = mutableListOf<ComponentModel>()

    fun text(text: String, style: String = "body") {
        components.add(ComponentModel.Text(text, style))
    }

    fun image(url: String, alt: String = "") {
        components.add(ComponentModel.Image(url, alt))
    }

    fun button(label: String, action: String) {
        components.add(ComponentModel.Button(label, action))
    }

    fun spacer(height: Int = 8) {
        components.add(ComponentModel.Spacer(height))
    }

    fun build(): ScreenModel = ScreenModel(id, title, components.toList())
}

fun screen(id: String, block: ScreenBuilder.() -> Unit): ScreenModel =
    ScreenBuilder(id).apply(block).build()
