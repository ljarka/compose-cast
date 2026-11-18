package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.server.sdk.RegistryBuilder

/**
 * One button per action type, for checking action handling on a device.
 */
internal fun RegistryBuilder.actionsScreen() {
    screen("actions") {
        title = "Actions"
        text("Every action type", style = "h2")
        button("Open URL", Action.OpenUrl("https://example.com"))
        spacer(8)
        button("Reload", Action.Reload)
        spacer(8)
        button("Track event", Action.Track("demo_tap", mapOf("source" to "actions")))
        spacer(8)
        button("Custom", Action.Custom("hello"))
        spacer(8)
        button("Navigate with params", Action.Navigate("echo", mapOf("word" to "zażółć gęślą jaźń")))
        spacer(8)
        button("Back to home", Action.Navigate("main"))
    }
}
