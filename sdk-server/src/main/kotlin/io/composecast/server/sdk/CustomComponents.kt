package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.Component
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ScreenDefinition
import io.composecast.server.sdk.render.RcJsonComponents
import io.composecast.server.sdk.render.RemoteComposeRenderer
import java.util.Properties

/** Custom components written in raw RC JSON (ADR 0033, T-049): checks without RC types in the API. */
public object CustomComponents {
    /** The Remote Compose release this server renders with; new definitions record it as `rcVersion`. */
    public val RC_VERSION: String = Properties().apply {
        CustomComponents::class.java.getResourceAsStream("rc-version.properties")?.use { load(it) }
    }.getProperty("version") ?: "unknown"

    /**
     * Every problem of [definition] with this server's RC version: its own fields, the whitelist and
     * a trial parse with sample arguments (the admin editor and the registry use it).
     */
    public fun check(definition: ComponentDefinition, brand: BrandConfig? = null): List<String> =
        RemoteComposeRenderer(brand, imageLoader = { error("custom components fetch no images") })
            .componentProblems(definition)

    /** Arguments for showing [definition] alone: its defaults, else a valid sample of each param type. */
    public fun sampleArgs(definition: ComponentDefinition): Map<String, String> =
        RcJsonComponents.sampleArgs(definition)

    /** [sampleArgs] of the params without a default: what a new use of [definition] must pass. */
    public fun requiredSampleArgs(definition: ComponentDefinition): Map<String, String> =
        sampleArgs(definition).filterKeys { definition.params[it]?.default == null }

    /** `(path, component)` of every [Component.Custom] in [screen] (components and error, nested). */
    public fun uses(screen: ScreenDefinition): List<Pair<String, Component.Custom>> = buildList {
        fun walk(list: List<Component>, path: String) {
            list.forEachIndexed { i, c ->
                val at = "$path[$i]"
                if (c is Component.Custom) add(at to c)
                c.childLists().forEach { (field, children) -> walk(children, "$at.$field") }
            }
        }
        walk(screen.components, "components")
        walk(screen.error, "error")
    }
}
