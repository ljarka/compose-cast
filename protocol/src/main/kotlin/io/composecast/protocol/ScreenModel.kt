package io.composecast.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ScreenModel(
    val id: String,
    val title: String,
    val components: List<ComponentModel> = emptyList()
)
