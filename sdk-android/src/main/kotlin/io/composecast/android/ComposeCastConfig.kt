package io.composecast.android

data class ComposeCastConfig(
    val serverUrl: String,
    val playerVersion: String = "1.0",
    val logEnabled: Boolean = false,
    val connectTimeoutMs: Long = 10_000L,
    val requestTimeoutMs: Long = 15_000L,
    val socketTimeoutMs: Long = 15_000L,
)
