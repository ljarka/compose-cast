package io.composecast.android

import io.ktor.client.plugins.ResponseException

/**
 * Why loading from the server failed, for showing the right message without depending on
 * exception types: `runCatching { client.getScreen("main") }.onFailure { show(it.toComposeCastError()) }`.
 */
public sealed interface ComposeCastError {
    /** Human-readable description (English); show your own text for end users. */
    public val message: String

    /**
     * The server answered 426 Upgrade Required: this app's Remote Compose player is older than
     * [minApiLevel], the screen needs an app update (T-004).
     */
    public data class UpgradeRequired(val minApiLevel: Int, override val message: String) : ComposeCastError

    /** The server answered with a non-2xx [statusCode] (e.g. 404 unknown screen, 400 missing parameter). */
    public data class Http(val statusCode: Int, override val message: String) : ComposeCastError

    /**
     * The server was not reached or its answer could not be read (no network, DNS, timeout, TLS,
     * malformed response). Retrying later may help.
     */
    public data class Network(val cause: Throwable) : ComposeCastError {
        override val message: String get() = cause.message ?: cause::class.java.simpleName
    }
}

/**
 * Classifies an exception thrown by [ComposeCastClient]. A [kotlinx.coroutines.CancellationException] is not an
 * error: rethrow it instead of calling this (coroutine cancellation must propagate).
 */
public fun Throwable.toComposeCastError(): ComposeCastError = when (this) {
    is ComposeCastUpgradeRequiredException -> ComposeCastError.UpgradeRequired(minApiLevel, messageOrDefault())
    is ComposeCastHttpException -> ComposeCastError.Http(status, messageOrDefault())
    // Defensive: the client converts these itself, but Ktor may throw them from future calls.
    is ResponseException -> ComposeCastError.Http(response.status.value, "HTTP ${response.status.value}")
    else -> ComposeCastError.Network(this)
}

private fun Throwable.messageOrDefault(): String = message ?: "HTTP error"
