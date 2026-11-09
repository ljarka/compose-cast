package io.composecast.protocol

import kotlinx.serialization.Serializable

/**
 * A consistent error response returned by all endpoints on 4xx/5xx.
 *
 * @param code  Machine-readable error code, e.g. `"screen_not_found"`.
 * @param message Human-readable description of what went wrong.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)
