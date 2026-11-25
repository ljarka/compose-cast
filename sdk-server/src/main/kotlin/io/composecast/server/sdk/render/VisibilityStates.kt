package io.composecast.server.sdk.render

import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcScope
import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import androidx.compose.remote.core.operations.layout.Component as RcComponent

/**
 * On-device visibility state variables allocated for one render pass.
 *
 * Each distinct `modifier.visibility.id` gets an `RcInteger` holding `VISIBLE (1)` or `GONE (0)`
 * plus a pre-emitted toggle expression (`1 - state`) in `Global { ... }`, so any component anywhere
 * in the screen can toggle, show, or hide any target group with zero server round trips.
 */
internal class VisibilityStates private constructor(private val entries: Map<String, Entry>) {
    data class Entry(val state: RcInteger, val toggled: RcInteger)

    operator fun get(id: String): Entry? = entries[id]

    companion object {
        val EMPTY = VisibilityStates(emptyMap())

        fun allocate(scope: RcScope, screen: ScreenDefinition): VisibilityStates {
            val initialById = linkedMapOf<String, Boolean>()
            fun walk(list: List<Component>) {
                for (c in list) {
                    c.modifier.visibility?.let { vis ->
                        if (vis.id.isNotBlank() && vis.id !in initialById) {
                            initialById[vis.id] = vis.hidden
                        }
                    }
                    c.childLists().forEach { (_, children) -> walk(children) }
                }
            }
            walk(screen.components)
            if (initialById.isEmpty()) return EMPTY

            val entries = linkedMapOf<String, Entry>()
            scope.Global {
                initialById.forEach { (id, hidden) ->
                    // Use remoteNamedInteger (not remoteInteger, which deduplicates equal values via
                    // mState.cacheInteger and would alias distinct visibility groups that share an
                    // initial state).
                    val initial = if (hidden) RcComponent.Visibility.GONE else RcComponent.Visibility.VISIBLE
                    val state = remoteNamedInteger("vis:$id", initial)
                    val toggled = (state + 1) % 2
                    entries[id] = Entry(state = state, toggled = toggled)
                }
            }
            return VisibilityStates(entries)
        }
    }
}
