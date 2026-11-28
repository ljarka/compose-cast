package io.composecast.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec

/**
 * Back stack of server-driven screens for [ComposeCastFlow] (T-059). Each entry is an
 * [Action.Navigate] (screen id + query parameters), the same value a `navigate:` button emits, so a
 * button's action can be pushed as is. All properties are Compose state: reading them in
 * composition recomposes on change. Call the mutators from the main thread.
 *
 * Create it with [rememberComposeCastNavigator] (survives configuration changes and process death).
 *
 * @param initialBackStack entries bottom first; may be empty (e.g. while the host looks up the home
 *   screen), then [ComposeCastFlow] shows its loading slot until [navigate] or [replaceRoot].
 */
public class ComposeCastNavigator(initialBackStack: List<Action.Navigate> = emptyList()) {
    /** Entries bottom first; the last one is shown. */
    public var backStack: List<Action.Navigate> by mutableStateOf(initialBackStack.toList())
        private set

    /** The shown entry, or null while the back stack is empty. */
    public val current: Action.Navigate? get() = backStack.lastOrNull()

    /** Whether [pop] would go back (more than one entry). */
    public val canGoBack: Boolean get() = backStack.size > 1

    /** Increased by [reload]; [ComposeCastFlow] fetches the current screen again when it changes. */
    public var reloadCount: Int by mutableIntStateOf(0)
        private set

    /** Opens [screen] with [params] on top of the back stack. */
    public fun navigate(screen: String, params: Map<String, String> = emptyMap()) {
        navigate(Action.Navigate(screen, params))
    }

    /** Opens [action]'s screen on top of the back stack (e.g. a button's `navigate:` action). */
    public fun navigate(action: Action.Navigate) {
        backStack = backStack + action
    }

    /** Replaces the whole back stack with [screen] (e.g. after login, or another home screen). */
    public fun replaceRoot(screen: String, params: Map<String, String> = emptyMap()) {
        backStack = listOf(Action.Navigate(screen, params))
    }

    /** Goes back one screen; false (and nothing changes) when only the root is left. */
    public fun pop(): Boolean {
        if (!canGoBack) return false
        backStack = backStack.dropLast(1)
        return true
    }

    /** Fetches the current screen again (pull-to-refresh, a `reload:` action). */
    public fun reload() {
        reloadCount++
    }

    internal companion object {
        /** Entries as their action wire form ([ActionCodec]): Bundle-friendly strings. */
        val Saver: Saver<ComposeCastNavigator, ArrayList<String>> = Saver(
            save = { navigator -> ArrayList(navigator.backStack.map(ActionCodec::encode)) },
            restore = { saved ->
                ComposeCastNavigator(saved.mapNotNull { ActionCodec.decode(it) as? Action.Navigate })
            },
        )
    }
}

/**
 * A [ComposeCastNavigator] starting at [startScreen] with [startParams], saved across configuration
 * changes and process death. [startScreen] `null` starts with an empty back stack: call
 * [ComposeCastNavigator.replaceRoot] when the first screen is known (e.g. from
 * [ComposeCastClient.getBrand]'s `homeScreen`).
 */
@Composable
public fun rememberComposeCastNavigator(
    startScreen: String? = "main",
    startParams: Map<String, String> = emptyMap(),
): ComposeCastNavigator = rememberSaveable(saver = ComposeCastNavigator.Saver) {
    ComposeCastNavigator(listOfNotNull(startScreen?.let { Action.Navigate(it, startParams) }))
}
