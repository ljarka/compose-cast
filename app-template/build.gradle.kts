// The demo app is built exactly like an app repository (T-024): only this plugin,
// compose-cast.properties and (optionally) an icon — no Kotlin, manifest or resources.
// Everything is configured in compose-cast.properties; the plugin is gradle-plugins/.
plugins {
    id("io.composecast.app")
}
