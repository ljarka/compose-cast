package io.composecast.protocol

import kotlinx.serialization.Serializable

/**
 * Version metadata returned by the /version endpoint.
 *
 * @param serverVersion  SemVer string of the running server, e.g. `"1.0.0"`.
 * @param protocolVersion SemVer string of the protocol this server speaks, e.g. `"1.0.0"`.
 */
@Serializable
data class VersionInfo(val serverVersion: String, val protocolVersion: String)
