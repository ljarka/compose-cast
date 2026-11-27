package io.composecast.protocol

import kotlinx.serialization.Serializable

/**
 * A consistent error response returned by all endpoints on 4xx/5xx.
 *
 * @param code  Machine-readable error code, e.g. `"screen_not_found"`.
 * @param message Human-readable description of what went wrong.
 */
@Serializable
public data class ErrorResponse(val code: String, val message: String)

/** Returned with HTTP 426 Upgrade Required when the client's RC API level is too old (T-004). */
@Serializable
public data class UpgradeRequiredResponse(val error: String, val minApiLevel: Int)
